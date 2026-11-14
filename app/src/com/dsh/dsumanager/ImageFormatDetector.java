package com.dsh.dsumanager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 安装包格式识别与预处理：
 * super.img / system.img / zip / tar / gz / sparse；zst/xz 尝试调用系统命令。
 */
public final class ImageFormatDetector {

    public interface LogSink {
        void log(String s);
    }

    public static final int KIND_SINGLE = 1;
    public static final int KIND_SUPER = 2;
    public static final int KIND_FOLDER = 3;
    public static final int KIND_MULTI = 4;   // zip/tar 里存在多个独立分区镜像

    public static final class PartFile {
        public String name;
        public File file;
        public long size;
    }

    public static final class Plan {
        public int kind;
        public String label = "";
        public File rawFile;
        public long size;
        public SuperImage.Info superInfo;
        public final ArrayList<SuperImage.Partition> partitions = new ArrayList<>();
        public final ArrayList<PartFile> partFiles = new ArrayList<>();
        public long totalBytes;
        public String error = "";
    }

    private static final String[] PARTITION_NAMES = {
            "system", "system_ext", "product", "vendor", "odm",
            "vendor_dlkm", "system_dlkm", "mi_ext"
    };

    /** 从 zip/tar 条目名识别分区名；无法识别返回 null。super.img 返回 "super"。 */
    private static String partitionNameFromEntry(String rawName) {
        if (rawName == null) return null;
        String name = rawName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String lower = name.toLowerCase();
        if (!lower.endsWith(".img")) return null;
        String base = lower.substring(0, lower.length() - 4);
        if (base.equals("super") || base.startsWith("super_")) return "super";
        if (base.endsWith("_a") || base.endsWith("_b")) base = base.substring(0, base.length() - 2);
        for (String p : PARTITION_NAMES) {
            if (base.equals(p)) return p;
        }
        return null;
    }

    public static Plan prepare(InputStream in, long declaredSize, File cacheDir, LogSink log) {
        Plan p = new Plan();
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File src = new File(cacheDir, "src_" + System.currentTimeMillis() + ".bin");
            log.log("复制安装包到缓存…");
            copy(in, src, declaredSize);
            File cur = src;
            for (int depth = 0; depth < 4; depth++) {
                String kind = sniff(cur);
                log.log("检测格式：" + kind + "（" + DsuManager.fmtBytes(cur.length()) + "）");
                switch (kind) {
                    case "zip": {
                        ArrayList<PartFile> parts = extractArchiveMulti(cur, cacheDir, true, log);
                        if (parts.isEmpty()) {
                            p.error = "压缩包里没有找到 super.img 或分区镜像（system/vendor/product…）";
                            return p;
                        }
                        PartFile superFile = findSuper(parts);
                        if (superFile != null) {
                            cur = superFile.file;
                            continue;
                        }
                        if (parts.size() == 1) {
                            cur = parts.get(0).file;
                            continue;
                        }
                        fillMulti(p, parts);
                        return p;
                    }
                    case "tar": {
                        ArrayList<PartFile> parts = extractArchiveMulti(cur, cacheDir, false, log);
                        if (parts.isEmpty()) {
                            p.error = "tar 里没有找到 super.img 或分区镜像（system/vendor/product…）";
                            return p;
                        }
                        PartFile superFile = findSuper(parts);
                        if (superFile != null) {
                            cur = superFile.file;
                            continue;
                        }
                        if (parts.size() == 1) {
                            cur = parts.get(0).file;
                            continue;
                        }
                        fillMulti(p, parts);
                        return p;
                    }
                    case "gz": {
                        cur = gunzip(cur, cacheDir, log);
                        continue;
                    }
                    case "sparse": {
                        cur = sparseToRaw(cur, cacheDir, log);
                        continue;
                    }
                    case "zst":
                    case "xz": {
                        File out = externalDecompress(cur, kind, cacheDir, log);
                        if (out == null) {
                            p.error = "设备缺少 " + kind + " 解压命令，请先在 PC 解压成 .img 再安装";
                            return p;
                        }
                        cur = out;
                        continue;
                    }
                    case "super": {
                        SuperImage.Info info = SuperImage.parse(cur);
                        if (info.partitions.isEmpty()) {
                            p.error = "super.img 解析失败：" + info.error;
                            return p;
                        }
                        p.kind = KIND_SUPER;
                        p.rawFile = cur;
                        p.superInfo = info;
                        p.partitions.addAll(info.partitions);
                        sortPartitions(p.partitions);
                        for (SuperImage.Partition sp : p.partitions) p.totalBytes += sp.size;
                        p.size = cur.length();
                        p.label = "super.img（" + p.partitions.size() + " 个分区）";
                        return p;
                    }
                    case "ext4":
                    case "erofs":
                    case "f2fs":
                    case "raw": {
                        p.kind = KIND_SINGLE;
                        p.rawFile = cur;
                        p.size = cur.length();
                        p.totalBytes = cur.length();
                        p.label = "系统镜像（" + kind + "）";
                        return p;
                    }
                    default:
                        p.error = "无法识别的格式：" + kind;
                        return p;
                }
            }
            p.error = "嵌套层数过多，无法识别";
            return p;
        } catch (Throwable t) {
            p.error = "解析失败：" + t.getMessage();
            return p;
        }
    }

    // ---------------- 格式识别 ----------------

    public static String sniff(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            byte[] b = new byte[8];
            if (len < 8) return "raw";
            raf.readFully(b);
            if (b[0] == 'P' && b[1] == 'K') return "zip";
            if ((b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) return "gz";
            if ((b[0] & 0xFF) == 0x28 && (b[1] & 0xFF) == 0xB5
                    && (b[2] & 0xFF) == 0x2F && (b[3] & 0xFF) == 0xFD) return "zst";
            if ((b[0] & 0xFF) == 0xFD && b[1] == '7' && b[2] == 'z' && b[3] == 'X' && b[4] == 'Z') return "xz";
            if ((b[0] & 0xFF) == 0x3A && (b[1] & 0xFF) == 0xFF
                    && (b[2] & 0xFF) == 0x26 && (b[3] & 0xFF) == 0xED) return "sparse";
            if ((b[0] & 0xFF) == 0x37 && (b[1] & 0xFF) == 0x7A) return "7z";
            if (len > 4100) {
                raf.seek(4096);
                byte[] m = new byte[4];
                raf.readFully(m);
                if (le32(m, 0) == 0x616C4467) return "super";
            }
            if (len > 1100) {
                raf.seek(1024);
                byte[] sb = new byte[64];
                raf.readFully(sb);
                if ((sb[0] & 0xFF) == 0xE2 && (sb[1] & 0xFF) == 0xE1
                        && (sb[2] & 0xFF) == 0xF5 && (sb[3] & 0xFF) == 0xE0) return "erofs";
                if (sb[56] == 0x53 && sb[57] == (byte) 0xEF) return "ext4";
                if ((sb[0] & 0xFF) == 0x10 && (sb[1] & 0xFF) == 0x20
                        && (sb[2] & 0xFF) == 0xF5 && (sb[3] & 0xFF) == 0xF2) return "f2fs";
            }
            if (len > 300) {
                raf.seek(257);
                byte[] t = new byte[5];
                raf.readFully(t);
                if ("ustar".equals(new String(t, "US-ASCII"))) return "tar";
            }
            return "raw";
        }
    }

    // ---------------- zip / tar ----------------

    private static PartFile findSuper(ArrayList<PartFile> parts) {
        for (PartFile pf : parts) {
            if ("super".equals(pf.name)) return pf;
        }
        return null;
    }

    private static void fillMulti(Plan p, ArrayList<PartFile> parts) {
        parts.sort((a, b) -> {
            int wa = "system".equals(a.name) ? 0 : 1;
            int wb = "system".equals(b.name) ? 0 : 1;
            if (wa != wb) return wa - wb;
            return a.name.compareTo(b.name);
        });
        p.kind = KIND_MULTI;
        p.partFiles.addAll(parts);
        long total = 0;
        for (PartFile pf : parts) total += pf.size;
        p.totalBytes = total;
        p.size = total;
        p.label = "多分区镜像（" + parts.size() + " 个分区）";
    }

    private static ArrayList<PartFile> extractArchiveMulti(File src, File cacheDir, boolean zip,
                                                           LogSink log) throws IOException {
        ArrayList<PartFile> out = new ArrayList<>();
        if (zip) extractZipMulti(src, cacheDir, out, log);
        else extractTarMulti(src, cacheDir, out, log);
        return out;
    }

    private static File cacheFile(File cacheDir, String name) {
        return new File(cacheDir, "extract_" + name + "_" + System.currentTimeMillis() + ".img");
    }

    private static void extractZipMulti(File src, File cacheDir, ArrayList<PartFile> out,
                                        LogSink log) throws IOException {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(src))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String part = partitionNameFromEntry(e.getName());
                if (part == null || !seen.add(part)) continue;
                File f = cacheFile(cacheDir, part);
                long n = copyTo(zin, f);
                if (n <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    continue;
                }
                PartFile pf = new PartFile();
                pf.name = part;
                pf.file = f;
                pf.size = n;
                out.add(pf);
                log.log("从 zip 提取分区：" + part + "（" + DsuManager.fmtBytes(n) + "）");
                if ("super".equals(part)) return; // super.img 包含全部分区，无需继续
            }
        }
    }

    /** 基础 tar 解析（ustar/GNU longname 支持）。 */
    private static void extractTarMulti(File src, File cacheDir, ArrayList<PartFile> out,
                                        LogSink log) throws IOException {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        try (RandomAccessFile raf = new RandomAccessFile(src, "r")) {
            long len = raf.length();
            long pos = 0;
            String longName = null;
            byte[] hdr = new byte[512];
            while (pos + 512 <= len) {
                raf.seek(pos);
                raf.readFully(hdr);
                boolean zero = true;
                for (byte x : hdr) if (x != 0) { zero = false; break; }
                if (zero) break;
                String name = longName != null ? longName
                        : new String(hdr, 0, 100, "UTF-8").split("\0")[0];
                long size = parseOctal(hdr, 124, 12);
                char type = (char) (hdr[156] & 0xFF);
                long dataPos = pos + 512;
                if (type == 'L') {
                    byte[] nm = new byte[(int) size];
                    raf.seek(dataPos);
                    raf.readFully(nm);
                    longName = new String(nm, "UTF-8").split("\0")[0];
                } else if (type == 'x' || type == 'g') {
                    // pax 头：忽略
                } else {
                    String part = partitionNameFromEntry(name);
                    if (part != null && seen.add(part)) {
                        File f = cacheFile(cacheDir, part);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            raf.seek(dataPos);
                            byte[] buf = new byte[256 * 1024];
                            long left = size;
                            while (left > 0) {
                                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                                if (n <= 0) break;
                                fos.write(buf, 0, n);
                                left -= n;
                            }
                        }
                        PartFile pf = new PartFile();
                        pf.name = part;
                        pf.file = f;
                        pf.size = f.length();
                        out.add(pf);
                        log.log("从 tar 提取分区：" + part + "（" + DsuManager.fmtBytes(pf.size) + "）");
                        if ("super".equals(part)) return;
                    }
                }
                pos = dataPos + ((size + 511) / 512) * 512;
            }
        }
    }

    private static long parseOctal(byte[] b, int off, int len) {
        String s = new String(b, off, len).trim().split("\0")[0].trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long copyTo(InputStream in, File out) throws IOException {
        long total = 0;
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                total += n;
            }
        }
        return total;
    }

    // ---------------- gz / sparse / zst/xz ----------------

    private static File gunzip(File src, File cacheDir, LogSink log) throws IOException {
        File out = new File(cacheDir, "gz_" + System.currentTimeMillis() + ".img");
        try (GZIPInputStream gz = new GZIPInputStream(new FileInputStream(src))) {
            copyTo(gz, out);
        }
        log.log("gzip 解压完成");
        return out;
    }

    private static File sparseToRaw(File src, File cacheDir, LogSink log) throws IOException {
        File out = new File(cacheDir, "raw_" + System.currentTimeMillis() + ".img");
        try (RandomAccessFile raf = new RandomAccessFile(src, "r");
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] hdr = new byte[28];
            raf.readFully(hdr);
            long totalBlks = le32u(hdr, 16);
            long totalChunks = le32u(hdr, 20);
            int blkSize = le32(hdr, 12);
            int fileHdrSize = le16(hdr, 8);
            int chunkHdrSize = le16(hdr, 10);
            raf.seek(fileHdrSize);
            byte[] chunk = new byte[12];
            byte[] buf = new byte[1024 * 1024];
            long done = 0;
            for (long c = 0; c < totalChunks; c++) {
                raf.readFully(chunk);
                int type = le16(chunk, 0);
                long chunkBlocks = le32u(chunk, 4);
                long totalSize = le32u(chunk, 8);
                long dataLen = totalSize - chunkHdrSize;
                if (type == 0xCAC1) { // RAW
                    long left = dataLen;
                    while (left > 0) {
                        int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                        if (n <= 0) break;
                        fos.write(buf, 0, n);
                        left -= n;
                    }
                } else if (type == 0xCAC2) { // FILL
                    byte[] pat = new byte[4];
                    int pr = raf.read(pat, 0, (int) Math.min(4, dataLen));
                    if (dataLen > pr) raf.seek(raf.getFilePointer() + (dataLen - pr));
                    long left2 = chunkBlocks * blkSize;
                    while (left2 > 0) {
                        int n = (int) Math.min(buf.length, left2);
                        for (int i = 0; i < n; i += 4)
                            for (int k = 0; k < 4 && i + k < n; k++) buf[i + k] = pat[k];
                        fos.write(buf, 0, n);
                        left2 -= n;
                    }
                } else if (type == 0xCAC3) { // DONT_CARE
                    long left2 = chunkBlocks * blkSize;
                    byte[] z = new byte[1024 * 1024];
                    while (left2 > 0) {
                        int n = (int) Math.min(z.length, left2);
                        fos.write(z, 0, n);
                        left2 -= n;
                    }
                } else if (type == 0xCAC4) {
                    raf.seek(raf.getFilePointer() + dataLen);
                } else {
                    raf.seek(raf.getFilePointer() + dataLen);
                }
                done += chunkBlocks;
                if (c % 32 == 0) log.log("sparse 转换：" + DsuManager.fmtBytes(done * blkSize)
                        + " / " + DsuManager.fmtBytes(totalBlks * blkSize));
            }
        }
        log.log("sparse 转 raw 完成");
        return out;
    }

    private static File externalDecompress(File src, String kind, File cacheDir, LogSink log) {
        try {
            String tool = "zst".equals(kind) ? "zstd" : "xz";
            RootShell.Result check = RootShell.runRoot("command -v " + tool + " 2>/dev/null", 30);
            if (!check.ok() || check.out.trim().isEmpty()) return null;
            File out = new File(cacheDir, tool + "_" + System.currentTimeMillis() + ".img");
            String cmd;
            if ("zst".equals(kind)) {
                cmd = "zstd -d -c '" + q(src.getAbsolutePath()) + "' > '" + q(out.getAbsolutePath()) + "'";
            } else {
                cmd = "xz -d -c '" + q(src.getAbsolutePath()) + "' > '" + q(out.getAbsolutePath()) + "'";
            }
            RootShell.Result r = RootShell.runRoot(cmd, 3600);
            if (r.ok() && out.exists() && out.length() > 0) {
                log.log(tool + " 解压完成");
                return out;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String q(String s) {
        return s.replace("'", "'\\''");
    }

    // ---------------- 工具 ----------------

    public static void sortPartitions(ArrayList<SuperImage.Partition> parts) {
        parts.sort(Comparator.comparingInt(p -> {
            for (int i = 0; i < DsuManager.ROM_PARTITIONS.length; i++) {
                if (DsuManager.ROM_PARTITIONS[i].equals(p.name)) return i;
            }
            return 100;
        }));
    }

    private static void copy(InputStream in, File out, long declaredSize) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8)
                | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static long le32u(byte[] b, int o) {
        return le32(b, o) & 0xFFFFFFFFL;
    }

    private static long le64(byte[] b, int o) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xFF);
        return v;
    }

    private ImageFormatDetector() {}
}
