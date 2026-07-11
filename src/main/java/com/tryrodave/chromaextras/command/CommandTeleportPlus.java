package com.tryrodave.chromaextras.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

/**
 * {@code /tpp} - "teleport plus": the modern-Minecraft teleport command backported to 1.7.10, mainly so camera
 * positions (e.g. custom main-menu panorama shots) can be reproduced exactly, including the view angle - which
 * vanilla 1.7.10 {@code /tp} cannot set.
 *
 * <pre>
 *   /tpp &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw] [pitch]            - teleport yourself
 *   /tpp &lt;player&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw] [pitch]   - teleport another player
 * </pre>
 *
 * All five numeric arguments accept modern relative notation ({@code ~} / {@code ~1.5}), resolved against the
 * <em>target's</em> current position/rotation. Omitted yaw/pitch keep the target's current rotation. Requires
 * permission level 2 (OP), the same as vanilla {@code /tp}; in singleplayer that means cheats enabled.
 */
public class CommandTeleportPlus extends CommandBase {

    @Override
    public String getCommandName() {
        return "tpp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tpp [player] <x> <y> <z> [yaw] [pitch]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // same as vanilla /tp
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException(this.getCommandUsage(sender));
        }

        // Decide whether args[0] is a player name/selector or the X coordinate: coordinates start with a digit,
        // '~', '-', '+' or '.', names/selectors don't.
        boolean hasTargetArg = !isCoordinate(args[0]);
        int base = hasTargetArg ? 1 : 0;
        if (args.length < base + 3 || args.length > base + 5) {
            throw new WrongUsageException(this.getCommandUsage(sender));
        }

        EntityPlayerMP target;
        if (hasTargetArg) {
            target = getPlayer(sender, args[0]); // resolves names and @-selectors, throws if no match
        } else if (sender instanceof EntityPlayerMP) {
            target = (EntityPlayerMP) sender;
        } else {
            throw new WrongUsageException("A non-player must specify a target: " + this.getCommandUsage(sender));
        }

        double x = parseRelative(target.posX, args[base]);
        double y = parseRelative(target.posY, args[base + 1]);
        double z = parseRelative(target.posZ, args[base + 2]);
        float yaw = args.length > base + 3 ? (float) parseRelative(target.rotationYaw, args[base + 3])
            : target.rotationYaw;
        float pitch = args.length > base + 4 ? (float) parseRelative(target.rotationPitch, args[base + 4])
            : target.rotationPitch;

        pitch = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(pitch), -90F, 90F);
        yaw = MathHelper.wrapAngleTo180_float(yaw);

        target.mountEntity(null); // dismount first, like vanilla /tp
        target.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, pitch);

        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    "Teleported %s to %.2f, %.2f, %.2f (yaw %.1f, pitch %.1f)",
                    target.getCommandSenderName(),
                    x,
                    y,
                    z,
                    yaw,
                    pitch)));
    }

    /** True when the argument reads as a (possibly relative) number rather than a player name or selector. */
    private static boolean isCoordinate(String arg) {
        if (arg.isEmpty()) {
            return false;
        }
        char c = arg.charAt(0);
        return c == '~' || c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9');
    }

    /** Modern relative parsing: {@code ~} = base, {@code ~d} = base + d, plain number = absolute. */
    private static double parseRelative(double base, String arg) {
        String s = arg;
        boolean relative = s.startsWith("~");
        if (relative) {
            s = s.substring(1);
            if (s.isEmpty()) {
                return base;
            }
        }
        try {
            double d = Double.parseDouble(s);
            return relative ? base + d : d;
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        // Only the optional first argument names a player; coordinates have no useful completion.
        return args.length == 1 ? getListOfStringsMatchingLastWord(
            args,
            MinecraftServer.getServer()
                .getAllUsernames())
            : null;
    }
}
