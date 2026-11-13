package com.dsh.dsumanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * super.img（动态分区镜像）解析、Android sparse 转换、分区流式读取。
 * 只依赖 java.io / java.nio，方便在 PC 上单测。
 */
public final class SuperImage {

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static final class Partition {
        public String name = "";
        public long size = 0;
        public final ArrayList<long[]> extents = new ArrayList<>(); // {offset, length, isZero}
    }

    public static final class Info {
        public final ArrayList<Partition> partitions = new ArrayList<>();
        public String error = "";
        public long sourceSize = 0;
    }

    private static final int LP_SECTOR = 512;
    private static final long SPARSE_MAGIC = 0xED26FF3AL;
    private static final int GEOM_MAGIC = 0x616C4467;
    private static final int META_MAGIC = 0x414C5030;

    // ---------------- sparse -> raw ----------------

    public static boolean isSparse(byte[] head4) {
        if (head4 == null || head4.length < 4) return false;
        long v = (head4[0] & 0xFFL) | ((head4[1] & 0xFFL) << 8)
                | ((head4[2] & 0xFFL) << 16) | ((head4[3] & 0xFFL) << 24);
        return v == SPARSE_MAGIC;
    }

    public static void copyToRaw(InputStream in, OutputStream out, long sourceSize,
                                 Progress cb) throws IOException {
        byte[] head = new byte[4];
        if (readFully(in, head, 4) < 4) throw new IOException("镜像为空");
        if (!isSparse(head)) {
            out.write(head);
            long done = 4;
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (cb != null) cb.onProgress(done, sourceSize);
            }
            return;
        }
        byte[] rest = new byte[24];
        if (readFully(in, rest, 24) < 24) throw new IOException("sparse 头不完整");
        byte[] hdr = new byte[28];
        System.arraycopy(head, 0, hdr, 0, 4);
        System.arraycopy(rest, 0, hdr, 4, 24);
        ByteBuffer hb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        int fileHdrSize = hb.getShort(8) & 0xFFFF;
        int chunkHdrSize = hb.getShort(10) & 0xFFFF;
        int blkSize = hb.getInt(12);
        long totalBlks = hb.getInt(16) & 0xFFFFFFFFL;
        long totalChunks = hb.getInt(20) & 0xFFFFFFFFL;
        if (blkSize <= 0) throw new IOException("sparse blkSize 异常");
        if (fileHdrSize > 28) skipFully(in, fileHdrSize - 28);

        byte[] chunkHdr = new byte[12];
        byte[] buf = new byte[1024 * 1024];
        long outBlocks = 0;
        for (long c = 0; c < totalChunks; c++) {
            if (readFully(in, chunkHdr, 12) < 12) throw new IOException("sparse chunk 不完整");
            ByteBuffer cb2 = ByteBuffer.wrap(chunkHdr).order(ByteOrder.LITTLE_ENDIAN);
            int type = cb2.getShort(0) & 0xFFFF;
            long chunkBlocks = cb2.getInt(4) & 0xFFFFFFFFL;
            long totalSize = cb2.getInt(8) & 0xFFFFFFFFL;
            long dataLen = Math.max(0, totalSize - chunkHdrSize);

            if (type == 0xCAC1) { // RAW
                long left = dataLen;
                while (left > 0) {
                    int n = (int) Math.min((long) buf.length, left);
                    int r = in.read(buf, 0, n);
                    if (r <= 0) throw new IOException("sparse RAW 数据不完整");
                    out.write(buf, 0, r);
                    left -= r;
                }
            } else if (type == 0xCAC2) { // FILL
                byte[] pat = new byte[4];
                int pr = readFully(in, pat, (int) Math.min(4, dataLen));
                if (dataLen > pr) skipFully(in, dataLen - pr);
                long bytes = chunkBlocks * blkSize;
                long left = bytes;
                while (left > 0) {
                    int n = (int) Math.min((long) buf.length, left);
                    for (int i = 0; i < n; i += 4) {
                        for (int k = 0; k < 4 && i + k < n; k++) buf[i + k] = pat[k];
                    }
                    out.write(buf, 0, n);
                    left -= n;
                }
            } else if (type == 0xCAC3) { // DONT_CARE
                long left = chunkBlocks * blkSize;
                while (left > 0) {
                    int n = (int) Math.min((long) buf.length, left);
                    Arrays.fill(buf, 0, n, (byte) 0);
                    out.write(buf, 0, n);
                    left -= n;
                }
            } else if (type == 0xCAC4) { // CRC32
                if (dataLen > 0) skipFully(in, dataLen);
            } else {
                if (dataLen > 0) skipFully(in, dataLen);
            }
            outBlocks += chunkBlocks;
            if (cb != null) cb.onProgress(outBlocks * blkSize, totalBlks * blkSize);
        }
    }

    // ---------------- LP 解析 ----------------

    public static Info parse(File file) {
        Info info = new Info();
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            long fileLen = f.length();
            info.sourceSize = fileLen;
            if (fileLen < 4096 + 4096) {
                info.error = "文件太小，不是 super.img";
                return info;
            }
            f.seek(4096);
            byte[] geom = new byte[52];
            f.readFully(geom);
            if (le32(geom, 0) != GEOM_MAGIC) {
                info.error = "不是 super.img（geometry magic 不符）";
                return info;
            }
            int maxSize = le32(geom, 40);
            int slotCount = le32(geom, 44);
            if (maxSize <= 0 || maxSize > 1024 * 1024) maxSize = 65536;
            if (slotCount <= 0 || slotCount > 8) slotCount = 3;

            long metaBase = -1;
            long[] candidates = {12288, 16384, 4096, 8192, 20480, 24576, 28672, 32768};
            for (long off : candidates) {
                if (off + 128 > fileLen) continue;
                f.seek(off);
                byte[] m = new byte[4];
                f.readFully(m);
                if (le32(m, 0) == META_MAGIC) { metaBase = off; break; }
            }
            if (metaBase < 0) { info.error = "找不到 LP 元数据"; return info; }

            for (int slot = 0; slot < slotCount; slot++) {
                long off = metaBase + (long) slot * maxSize;
                if (off + 128 > fileLen) continue;
                f.seek(off);
                byte[] hdr = new byte[256];
                f.readFully(hdr);
                if (le32(hdr, 0) != META_MAGIC) continue;
                int headerSize = le32(hdr, 8);
                int tablesSize = le32(hdr, 44);
                if (tablesSize <= 0 || tablesSize > 4 * 1024 * 1024) continue;
                int partOff = le32(hdr, 80), partNum = le32(hdr, 84), partEsz = le32(hdr, 88);
                int extOff = le32(hdr, 92), extNum = le32(hdr, 96), extEsz = le32(hdr, 100);
                if (partEsz < 52 || extEsz < 24 || partNum < 0 || extNum < 0) continue;
                f.seek(off + headerSize);
                byte[] tables = new byte[tablesSize];
                f.readFully(tables);

                ArrayList<String> names = new ArrayList<>();
                ArrayList<Integer> firstExt = new ArrayList<>();
                ArrayList<Integer> numExt = new ArrayList<>();
                for (int i = 0; i < partNum; i++) {
                    int o = partOff + i * partEsz;
                    if (o + 52 > tables.length) break;
                    names.add(cstr(tables, o, 36));
                    firstExt.add(le32(tables, o + 40));
                    numExt.add(le32(tables, o + 44));
                }
                ArrayList<long[]> exts = new ArrayList<>();
                for (int i = 0; i < extNum; i++) {
                    int o = extOff + i * extEsz;
                    if (o + 24 > tables.length) break;
                    long sectors = le64(tables, o);
                    int type = le32(tables, o + 8);
                    long dataSector = le64(tables, o + 12);
                    exts.add(new long[]{sectors, type, dataSector});
                }
                for (int i = 0; i < names.size(); i++) {
                    String name = stripSlot(names.get(i));
                    if (!isKnown(name)) continue;
                    Partition p = new Partition();
                    p.name = name;
                    int fe = firstExt.get(i);
                    int ne = numExt.get(i);
                    long total = 0;
                    for (int j = 0; j < ne; j++) {
                        int idx = fe + j;
                        if (idx < 0 || idx >= exts.size()) break;
                        long[] e = exts.get(idx);
                        long len = e[0] * LP_SECTOR;
                        long[] ex = new long[3];
                        if (e[1] == 0) {
                            ex[0] = e[2] * LP_SECTOR;
                            ex[1] = len;
                            ex[2] = 0;
                        } else {
                            ex[0] = 0;
                            ex[1] = len;
                            ex[2] = 1;
                        }
                        p.extents.add(ex);
                        total += len;
                    }
                    p.size = total;
                    if (p.size <= 0) continue;
                    boolean dup = false;
                    for (Partition q : info.partitions) {
                        if (q.name.equals(p.name)) { dup = true; break; }
                    }
                    if (!dup) info.partitions.add(p);
                }
                if (!info.partitions.isEmpty()) return info;
            }
            info.error = "没有解析到分区（可能不是标准 super.img）";
        } catch (Exception e) {
            info.error = "解析失败：" + e.getMessage();
        }
        return info;
    }

    public static InputStream openPartition(File file, Partition p) throws IOException {
        return new ExtentInputStream(file, p);
    }

    private static final class ExtentInputStream extends InputStream {
        private final ArrayList<long[]> exts;
        private RandomAccessFile raf;
        private int idx = 0;
        private long pos = 0;
        private boolean needSeek = true;

        ExtentInputStream(File file, Partition p) throws IOException {
            this.exts = p.extents;
            this.raf = new RandomAccessFile(file, "r");
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            while (idx < exts.size()) {
                long[] e = exts.get(idx);
                if (pos >= e[1]) {
                    idx++;
                    pos = 0;
                    needSeek = true;
                    continue;
                }
                int n = (int) Math.min((long) len, e[1] - pos);
                if (e[2] == 1) {
                    Arrays.fill(b, off, off + n, (byte) 0);
                } else {
                    if (needSeek) {
                        raf.seek(e[0] + pos);
                        needSeek = false;
                    }
                    raf.readFully(b, off, n);
                }
                pos += n;
                return n;
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            try { raf.close(); } catch (Exception ignored) { }
        }
    }

    // ---------------- helpers ----------------

    private static String stripSlot(String name) {
        if (name.endsWith("_a") || name.endsWith("_b")) return name.substring(0, name.length() - 2);
        return name;
    }

    private static boolean isKnown(String name) {
        for (String p : DsuManager.ROM_PARTITIONS) {
            if (p.equals(name)) return true;
        }
        return false;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.US_ASCII);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long le64(byte[] b, int off) {
        return (le32(b, off) & 0xFFFFFFFFL) | ((long) le32(b, off + 4) << 32);
    }

    private static int readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            n -= s;
        }
    }

    private SuperImage() {}
}
