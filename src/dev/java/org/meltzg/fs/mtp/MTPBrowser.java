package org.meltzg.fs.mtp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the directory tree of every connected MTP device and prints it as an ASCII tree.
 *
 * Usage:
 *   ./gradlew browse                  # unlimited depth
 *   ./gradlew browse --args="2"       # limit to 2 levels deep
 */
public class MTPBrowser {

    public static void main(String[] args) throws Exception {
        int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : Integer.MAX_VALUE;

        var bridge = MTPDeviceBridge.getInstance();
        if (bridge.getDeviceConns().isEmpty()) {
            System.out.println("No MTP devices connected.");
            return;
        }

        for (var entry : bridge.getDeviceInfo().entrySet()) {
            var id = entry.getKey();
            var info = entry.getValue();
            System.out.printf("%s  [%s]%n", info.getDescription(), id);

            var provider = new MTPFileSystemProvider();
            var uri = URI.create("mtp://" + id + "/");
            try (var fs = provider.newFileSystem(uri, null)) {
                printChildren(fs.getPath("/"), "", maxDepth, 0);
            }
            System.out.println();
        }
    }

    private static void printChildren(Path dir, String prefix, int maxDepth, int depth)
            throws IOException {
        if (depth >= maxDepth) return;

        List<Path> children = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            stream.forEach(children::add);
        } catch (IOException e) {
            System.out.println(prefix + "[error reading directory: " + e.getMessage() + "]");
            return;
        }

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            boolean last = (i == children.size() - 1);
            boolean isDir = Files.isDirectory(child);

            System.out.println(prefix + (last ? "└── " : "├── ")
                + child.getFileName() + (isDir ? "/" : ""));

            if (isDir) {
                printChildren(child, prefix + (last ? "    " : "│   "), maxDepth, depth + 1);
            }
        }
    }
}
