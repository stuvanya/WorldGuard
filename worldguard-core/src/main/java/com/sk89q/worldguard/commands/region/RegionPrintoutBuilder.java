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

import com.sk89q.worldguard.util.profile.cache.ProfileCache;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.ErrorFormat;
import com.sk89q.worldedit.util.formatting.component.LabelFormat;
import com.sk89q.worldedit.util.formatting.component.MessageBox;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

/**
 * Create a region printout, as used in /region info to show information about
 * a region.
 */
public class RegionPrintoutBuilder implements Callable<TextComponent> {

    private final String world;
    private final ProtectedRegion region;
    @Nullable
    private final ProfileCache cache;
    private final TextComponentProducer builder = new TextComponentProducer();
    private final RegionPermissionModel perms;

    /**
     * Create a new instance with a region to report on.
     *
     * @param region the region
     * @param cache a profile cache, or {@code null}
     */
    public RegionPrintoutBuilder(String world, ProtectedRegion region, @Nullable ProfileCache cache, @Nullable Actor actor) {
        this.world = world;
        this.region = region;
        this.cache = cache;
        this.perms = actor != null && actor.isPlayer() ? new RegionPermissionModel(actor) : null;
    }

    /**
     * Add a new line.
     */
    public void newline() {
        builder.append(TextComponent.newline());
    }
    
    /**
     * Add region name, type, and priority.
     */
    public void appendBasics() {
        builder.append(TextComponent.of("§eРегион: "));
        builder.append(TextComponent.of("§a" + region.getId())
                .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/rg info -w \"" + world + "\" " + region.getId())));
        
        builder.append(TextComponent.of(" (тип=", TextColor.GRAY));
        builder.append(TextComponent.of(region.getType().getName()));
        
        builder.append(TextComponent.of(", приоритет=", TextColor.GRAY));
        appendPriorityComponent(region);
        builder.append(TextComponent.of(")", TextColor.GRAY));

        newline();
    }

    /**
     * Add information about flags.
     */
    public void appendFlags() {
        builder.append(TextComponent.of("§eФлаги: "));
        
        appendFlagsList(true);
        
        newline();
    }
    
    /**
     * Append just the list of flags (without "Flags:"), including colors.
     *
     * @param useColors true to use colors
     */
    public void appendFlagsList(boolean useColors) {
        boolean hasFlags = false;
        
        for (Flag<?> flag : WorldGuard.getInstance().getFlagRegistry()) {
            Object val = region.getFlag(flag);

            // No value
            if (val == null) {
                continue;
            }

            if (hasFlags) {
                builder.append(TextComponent.of(", "));
            }

            RegionGroupFlag groupFlag = flag.getRegionGroupFlag();
            Object group = null;
            if (groupFlag != null) {
                group = region.getFlag(groupFlag);
            }

            String flagString;

            if (group == null) {
                flagString = flag.getName() + ": ";
            } else {
                flagString = flag.getName() + " -g " + group + ": ";
            }

            TextComponent flagText = TextComponent.of(flagString, useColors ? TextColor.GOLD : TextColor.WHITE)
                    .append(TextComponent.of(String.valueOf(val), useColors? TextColor.YELLOW : TextColor.WHITE));
            if (perms != null && perms.maySetFlag(region, flag)) {
                flagText = flagText.hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы добавить")))
                        .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                                "/rg flag -w \"" + world + "\" " + region.getId() + " " + flag.getName() + " "));
            }
            builder.append(flagText);

            hasFlags = true;
        }

        if (!hasFlags) {
            TextComponent noFlags = TextComponent.of("(нет)", useColors ? TextColor.RED : TextColor.WHITE);
            builder.append(noFlags);
        }

        if (perms != null && perms.maySetFlag(region)) {
            builder.append(TextComponent.space())
                    .append(TextComponent.of("[Флаги]", useColors ? TextColor.GREEN : TextColor.GRAY)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы добавить")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/rg flags -w \"" + world + "\" " + region.getId())));
        }
    }

    /**
     * Add information about parents.
     */
    public void appendParents() {
        appendParentTree(true);
    }
    
    /**
     * Add information about parents.
     * 
     * @param useColors true to use colors
     */
    public void appendParentTree(boolean useColors) {
        if (region.getParent() == null) {
            return;
        }
        
        List<ProtectedRegion> inheritance = new ArrayList<>();

        ProtectedRegion r = region;
        inheritance.add(r);
        while (r.getParent() != null) {
            r = r.getParent();
            inheritance.add(r);
        }

        ListIterator<ProtectedRegion> it = inheritance.listIterator(
                inheritance.size());

        ProtectedRegion last = null;
        int indent = 0;
        while (it.hasPrevious()) {
            ProtectedRegion cur = it.previous();

            StringBuilder namePrefix = new StringBuilder();
            
            // Put symbol for child
            if (indent != 0) {
                for (int i = 0; i < indent; i++) {
                    namePrefix.append(" ");
                }
                namePrefix.append("\u2937"); //⤷
            }

            // Put name
            builder.append(TextComponent.of(namePrefix.toString(), useColors ? TextColor.GREEN : TextColor.WHITE));
            if (perms != null && perms.mayLookup(cur)) {
                builder.append(TextComponent.of(cur.getId(), useColors ? TextColor.GREEN : TextColor.WHITE)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите для доп. информации")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/rg info -w \"" + world + "\" " + cur.getId())));
            } else {
                builder.append(TextComponent.of(cur.getId(), useColors ? TextColor.GREEN : TextColor.WHITE));
            }
            
            // Put (parent)
            if (!cur.equals(region)) {
                builder.append(TextComponent.of(" (родитель, приоритет=", useColors ? TextColor.GRAY : TextColor.WHITE));
                appendPriorityComponent(cur);
                builder.append(TextComponent.of(")", useColors ? TextColor.GRAY : TextColor.WHITE));
            }
            if (last != null && cur.equals(region) && perms != null && perms.maySetParent(cur, last)) {
                builder.append(TextComponent.space());
                builder.append(TextComponent.of("[X]", TextColor.RED)
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы убрать родителя")))
                        .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND, "/rg setparent -w \"" + world + "\" " + cur.getId())));
            }

            last = cur;
            indent++;
            newline();
        }
    }

    /**
     * Add information about members.
     */
    public void appendDomain() {
        builder.append(TextComponent.of("Владельцы: ", TextColor.YELLOW));
        addDomainString(region.getOwners(),
                perms != null && perms.mayAddOwners(region) ? "addowner" : null,
                perms != null && perms.mayRemoveOwners(region) ? "removeowner" : null);
        newline();

        builder.append(TextComponent.of("Участники: ", TextColor.YELLOW));
        addDomainString(region.getMembers(),
                perms != null && perms.mayAddMembers(region) ? "addmember" : null,
                perms != null && perms.mayRemoveMembers(region) ? "removemember" : null);
        newline();
    }

    private void addDomainString(DefaultDomain domain, String addCommand, String removeCommand) {
        if (domain.size() == 0) {
            builder.append(ErrorFormat.wrap("(нет)"));
        } else {
            if (perms != null) {
                builder.append(domain.toUserFriendlyComponent(cache));
            } else {
                builder.append(LabelFormat.wrap(domain.toUserFriendlyString(cache)));
            }
        }
        if (addCommand != null) {
            builder.append(TextComponent.space().append(TextComponent.of("[Добавить]", TextColor.GREEN)
                            .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы добавить")))
                            .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/rg " + addCommand + " -w \"" + world + "\" " + region.getId() + " "))));
        }
        if (removeCommand != null && domain.size() > 0) {
            builder.append(TextComponent.space().append(TextComponent.of("[Удалить]", TextColor.RED)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы удалить")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                            "/rg " + removeCommand + " -w \"" + world + "\" " + region.getId() + " "))));
            builder.append(TextComponent.space().append(TextComponent.of("[Очистить]", TextColor.RED)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы очистить")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                            "/rg " + removeCommand + " -w \"" + world + "\" -a " + region.getId()))));
        }
    }

    /**
     * Add information about coordinates.
     */
    public void appendBounds() {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        builder.append(TextComponent.of("Границы:", TextColor.YELLOW));
        TextComponent bound = TextComponent.of(" " + min + " -> " + max, TextColor.GREEN);
        if (perms != null && perms.maySelect(region)) {
            bound = bound
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите, чтобы выделить")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND, "/rg select " + region.getId()));
        }
        builder.append(bound);
        final Location teleFlag = region.getFlag(Flags.TELE_LOC);
        if (teleFlag != null && perms != null && perms.mayTeleportTo(region)) {
            builder.append(TextComponent.space().append(TextComponent.of("[Телепорт]", TextColor.GRAY)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT,
                            TextComponent.of("Нажмите, чтобы телепортироваться").append(TextComponent.newline()).append(
                                    TextComponent.of(teleFlag.getBlockY() + ", "
                                            + teleFlag.getBlockY() + ", "
                                            + teleFlag.getBlockZ()))))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                            "/rg tp -w \"" + world + "\" " + region.getId()))));
        }

        newline();
    }

    private void appendPriorityComponent(ProtectedRegion rg) {
        final String content = String.valueOf(rg.getPriority());
        if (perms != null && perms.maySetPriority(rg)) {
            builder.append(TextComponent.of(content, TextColor.GOLD)
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Нажмите. чтобы изменить")))
                    .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND, "/rg setpriority -w \"" + world + "\" " + rg.getId() + " ")));
        } else {
            builder.append(TextComponent.of(content, TextColor.WHITE));
        }
    }

    private void appendRegionInformation() {
        appendBasics();
        appendFlags();
        appendParents();
        appendDomain();
        appendBounds();

        // if (cache != null && perms == null) {
        //     builder.append(SubtleFormat.wrap("Any names suffixed by * are 'last seen names' and may not be up to date."));
        // }
    }

    @Override
    public TextComponent call() {
        MessageBox box = new MessageBox("Инфо о регионе", builder);
        appendRegionInformation();
        return box.create();
    }

    /**
     * Send the report to a {@link Actor}.
     *
     * @param sender the recipient
     */
    public void send(Actor sender) {
        sender.print(toComponent());
    }

    public TextComponentProducer append(String str) {
        return builder.append(TextComponent.of(str));
    }

    public TextComponentProducer append(TextComponent component) {
        return builder.append(component);
    }

    public TextComponent toComponent() {
        return builder.create();
    }

    @Override
    public String toString() {
        return builder.toString().trim();
    }

}
