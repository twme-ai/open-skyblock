package io.github.openskyblock.island;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.World;

final class WorldEditSchematicAdapter {
    private WorldEditSchematicAdapter() {
    }

    static boolean paste(World world, Path file, int x, int y, int z, boolean pasteAir, boolean pasteEntities) throws IOException, WorldEditException {
        if (world == null || file == null || !Files.isRegularFile(file)) {
            return false;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) {
            return false;
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file.toFile()))) {
            Clipboard clipboard = reader.read();
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(x, y, z))
                        .ignoreAirBlocks(!pasteAir)
                        .copyEntities(pasteEntities)
                        .build();
                Operations.complete(operation);
                return true;
            }
        }
    }
}
