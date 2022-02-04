package net.liplum.api.data;

import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;

public interface IDataBuilding {
    Building getBuilding();

    Tile getTile();

    Block getBlock();
}