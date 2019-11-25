/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.commands.region;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.command.util.AsyncCommandBuilder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.auth.AuthorizationException;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.util.DomainInputResolver;
import com.sk89q.worldguard.protection.util.DomainInputResolver.UserLocatorPolicy;

import java.util.concurrent.Callable;

public class MemberCommands extends RegionCommandsBase {

    private final WorldGuard worldGuard;

    public MemberCommands(WorldGuard worldGuard) {
        this.worldGuard = worldGuard;
    }

    @Command(aliases = {"addmember", "addmember", "addmem", "am"},
            usage = "<id> <members...>",
            flags = "nw:",
            desc = "Длбавить участника в регион",
            min = 2)
    public void addMember(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayAddMembers(region)) {
            throw new CommandPermissionsException();
        }

        // Resolve members asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);


        final String description = String.format("§5§l╠§a§lS-3D§5§l╣§r §bДобавление участников в регион §a'%s' §bв мире §a'%s'", region.getId(), world.getName());
        AsyncCommandBuilder.wrap(resolver, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess(String.format("§5§l╠§a§lS-3D§5§l╣§r §bВ регион §a'%s' §bбыли внесены следующие изменения: добавлен 1 или несколько участников.", region.getId()), region.getMembers()::addAll)
                .onFailure("§5§l╠§a§lS-3D§5§l╣§r §cОшибка при добалении новых участников", worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    @Command(aliases = {"addowner", "addowner", "ao"},
            usage = "<id> <owners...>",
            flags = "nw:",
            desc = "Добавить владельца в регион",
            min = 2)
    public void addOwner(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world

        String id = args.getString(0);

        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayAddOwners(region)) {
            throw new CommandPermissionsException();
        }

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);


        final String description = String.format("§5§l╠§a§lS-3D§5§l╣§r §bДобавление владельцев в регион §a'%s' §bв мире §a'%s'", region.getId(), world.getName());
        AsyncCommandBuilder.wrap(checkedAddOwners(sender, manager, region, world, resolver), sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess(String.format("§5§l╠§a§lS-3D§5§l╣§r §bВ регион §a'%s' §bбыли внесены следующие изменения: добавлен 1 или несколько владельцев.", region.getId()), region.getOwners()::addAll)
                .onFailure("§5§l╠§a§lS-3D§5§l╣§r §cОшибка при добалении новых владельцев", worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    private static Callable<DefaultDomain> checkedAddOwners(Actor sender, RegionManager manager, ProtectedRegion region,
                                                            World world, DomainInputResolver resolver) {
        return () -> {
            DefaultDomain owners = resolver.call();
            // TODO this was always broken and never checked other players
            if (sender instanceof LocalPlayer) {
                LocalPlayer player = (LocalPlayer) sender;
                if (owners.contains(player) && !sender.hasPermission("worldguard.region.unlimited")) {
                    int maxRegionCount = WorldGuard.getInstance().getPlatform().getGlobalStateManager()
                            .get(world).getMaxRegionCount(player);
                    if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(player)
                            >= maxRegionCount) {
                        throw new CommandException("§5§l╠§a§lS-3D§5§l╣§r §cВы исчерпали лимит для создания нового региона! Удалите какой-нибудь старый, чтобы создать новый.");
                    }
                }
            }
            if (region.getOwners().size() == 0) {
                boolean anyOwners = false;
                ProtectedRegion parent = region;
                while ((parent = parent.getParent()) != null) {
                    if (parent.getOwners().size() > 0) {
                        anyOwners = true;
                        break;
                    }
                }
                if (!anyOwners) {
                    sender.checkPermission("worldguard.region.addowner.unclaimed." + region.getId().toLowerCase());
                }
            }
            return owners;
        };
    }

    @Command(aliases = {"removemember", "remmember", "removemem", "remmem", "rm"},
            usage = "<id> <owners...>",
            flags = "naw:",
            desc = "Удалить участников из региона",
            min = 1)
    public void removeMember(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayRemoveMembers(region)) {
            throw new CommandPermissionsException();
        }

        Callable<DefaultDomain> callable;
        if (args.hasFlag('a')) {
            callable = region::getMembers;
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException("§5§l╠§a§lS-3D§5§l╣§r §cУкажите ники, кого хотите удалить, или используйте -a чтобы удалить всех.");
            }

            // Resolve members asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            callable = resolver;
        }

        final String description = String.format("§5§l╠§a§lS-3D§5§l╣§r §bУдаление участников из региона §a'%s' §bв мире §a'%s'", region.getId(), world.getName());
        AsyncCommandBuilder.wrap(callable, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .sendMessageAfterDelay("(Пожалуйста подождите... поиск игроков по никам...)")
                .onSuccess(String.format("§5§l╠§a§lS-3D§5§l╣§r §bВ регион §a'%s' §bбыли внесены следующие изменения: удалён 1 или несколько участников.", region.getId()), region.getMembers()::removeAll)
                .onFailure("§5§l╠§a§lS-3D§5§l╣§r §cОшибка при удалении участников", worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    @Command(aliases = {"removeowner", "remowner", "ro"},
            usage = "<id> <owners...>",
            flags = "naw:",
            desc = "Удалить владельцев из региона",
            min = 1)
    public void removeOwner(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayRemoveOwners(region)) {
            throw new CommandPermissionsException();
        }

        Callable<DefaultDomain> callable;
        if (args.hasFlag('a')) {
            callable = region::getOwners;
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException("§5§l╠§a§lS-3D§5§l╣§r §cУкажите ники, кого хотите удалить, или используйте -a чтобы удалить всех.");
            }

            // Resolve owners asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            callable = resolver;
        }

        final String description = String.format("§5§l╠§a§lS-3D§5§l╣§r §bУдаление владельцев из региона §a'%s' §bв мире §a'%s'", region.getId(), world.getName());
        AsyncCommandBuilder.wrap(callable, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .sendMessageAfterDelay("(Пожалуйста подождите... поиск игроков по никам...)")
                .onSuccess(String.format("§5§l╠§a§lS-3D§5§l╣§r §bВ регион §a'%s' §bбыли внесены следующие изменения: удалён 1 или несколько владельцев.", region.getId()), region.getOwners()::removeAll)
                .onFailure("§5§l╠§a§lS-3D§5§l╣§r §cОшибка при удалении владельцев", worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }
}
