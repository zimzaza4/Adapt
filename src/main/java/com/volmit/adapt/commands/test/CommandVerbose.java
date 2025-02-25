/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.commands.test;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

import java.util.List;

public class CommandVerbose extends MortarCommand {
    private static final List<String> permission = List.of("adapt.idontknowwhatimdoingiswear");

    public CommandVerbose() {
        super("verbose", "v");
        this.setDescription("This Toggles the Debug mode(Console Spam mode)");
    }

    @Override
    public List<String> getRequiredPermissions() {
        return permission;
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        AdaptConfig.get().setVerbose(!AdaptConfig.get().isVerbose());
        Adapt.messagePlayer(sender.player(), "Verbose: " + AdaptConfig.get().isVerbose());
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
