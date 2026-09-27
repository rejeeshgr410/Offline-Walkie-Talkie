package com.example.offlinewalkietalkie.group

/**
 * Decides which neighbours should get a relayed copy of a voice frame.
 *
 * Flooding every frame to every neighbour works but multiplies airtime in a well-connected
 * group: in a full mesh of four phones each frame would be sent three extra times for no
 * benefit. Instead, a device forwards a frame it got from [prevHop] only to neighbours
 * that [prevHop] cannot reach itself, and only if it is the lowest-id device able to bridge
 * that gap — so exactly one relay covers each hole.
 *
 * If the neighbour map is stale or missing, this degrades to flooding, which is safe
 * because duplicate frames are dropped by the seen-packet cache.
 */
object RelayPlanner {

    fun relayTargets(
        self: String,
        prevHop: String,
        neighbors: Map<String, Set<String>>
    ): Set<String> {
        val mine = neighbors[self].orEmpty()
        val prevReach = neighbors[prevHop]
        val result = mutableSetOf<String>()

        for (target in mine) {
            if (target == prevHop) continue
            if (prevReach != null && target in prevReach) continue

            // Everyone adjacent to both ends could bridge this gap; the lowest id does it.
            val bridges = mutableSetOf(self)
            if (prevReach != null) {
                for (candidate in prevReach) {
                    if (candidate == target) continue
                    val candReach = neighbors[candidate].orEmpty()
                    val targetReach = neighbors[target].orEmpty()
                    if (target in candReach || candidate in targetReach) bridges.add(candidate)
                }
            }
            if (bridges.min() == self) result.add(target)
        }
        return result
    }
}
