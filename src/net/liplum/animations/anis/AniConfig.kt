package net.liplum.animations.anis

import arc.util.Nullable
import mindustry.gen.Building
import mindustry.world.Block
import net.liplum.api.ITrigger
import java.util.*

/**
 * The configuration of an Animation State Machine
 *
 * @param <TBlock> the type of block which has this animation configuration
 * @param <TBuild> the corresponding [Building] type
</TBuild></TBlock> */
class AniConfig<TBlock : Block, TBuild : Building> {
    /**
     * Key --to--> When can go to the next State
     */
    private val canEnters = HashMap<Any, ITrigger<TBlock, TBuild>>()
    /**
     * Current State --to--> The next all possible State
     */
    private val allEntrances = HashMap<AniState<TBlock, TBuild>, MutableList<AniState<TBlock, TBuild>>>()
    /**
     * Gets the default State
     *
     * @return the default State
     */
    /**
     * [NotNull,lateinit] The default and initial State.
     */
    var defaultState: AniState<TBlock, TBuild>? = null
        private set
    /**
     * Whether this configuration has built
     */
    private var built = false
    /**
     * Which from State is configuring
     */
    @Nullable
    private var curConfiguringFromState: AniState<TBlock, TBuild>? = null
    /**
     * Which to State is configuring
     */
    @Nullable
    private var curConfiguringToState: AniState<TBlock, TBuild>? = null
    /**
     * Sets default State
     *
     * @param state the default State
     * @return this
     * @throws AlreadyBuiltException thrown when this configuration has already built
     */
    fun defaultState(state: AniState<TBlock, TBuild>): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        defaultState = state
        return this
    }
    /**
     * Creates an entry
     *
     * @param from     the current State
     * @param to       the next State
     * @param canEnter When the State Machine can go from current State to next State
     * @return this
     * @throws AlreadyBuiltException    thrown when this configuration has already built
     * @throws CannotEnterSelfException thrown when `from` equals to `to`
     */
    fun entry(
        from: AniState<TBlock, TBuild>, to: AniState<TBlock, TBuild>,
        canEnter: ITrigger<TBlock, TBuild>
    ): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        if (from === to || from.stateName == to.stateName) {
            throw CannotEnterSelfException(this.toString())
        }
        curConfiguringFromState = from
        val key = getKey(from, to)
        canEnters[key] = canEnter
        val entrances = allEntrances.computeIfAbsent(
            from
        ) { LinkedList() }
        entrances.add(to)
        return this
    }
    /**
     * Sets the "from" State
     *
     * @param from the current State
     * @return this
     */
    infix fun From(from: AniState<TBlock, TBuild>): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        curConfiguringFromState = from
        return this
    }
    /**
     * Creates an entry with "from" State
     *
     * @param to       the next State
     * @param canEnter When the State Machine can go from current State to next State
     * @return this
     */
    fun To(to: AniState<TBlock, TBuild>, canEnter: ITrigger<TBlock, TBuild>): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        if (curConfiguringFromState == null) {
            throw NoFromStateException(this.toString())
        }
        val ccf = curConfiguringFromState!!
        if (ccf == to || ccf.stateName == to.stateName) {
            throw CannotEnterSelfException(this.toString())
        }
        val key = getKey(ccf, to)
        canEnters[key] = canEnter
        val entrances = allEntrances.computeIfAbsent(
            ccf
        ) { LinkedList() }
        entrances.add(to)
        return this
    }
    /**
     * Sets the "to" State
     *
     * @param to the next State
     * @return this
     */
    infix fun To(to: AniState<TBlock, TBuild>): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        curConfiguringToState = to
        return this
    }
    /**
     * Creates an entry with "from" and "to" States
     *
     * @param canEnter When the State Machine can go from current State to next State
     * @return this
     */
    infix fun When(canEnter: ITrigger<TBlock, TBuild>): AniConfig<TBlock, TBuild> {
        if (built) {
            throw AlreadyBuiltException(this.toString())
        }
        if (curConfiguringFromState == null) {
            throw NoFromStateException(this.toString())
        }
        if (curConfiguringToState == null) {
            throw NoToStateException(this.toString())
        }
        if (curConfiguringFromState === curConfiguringToState || curConfiguringFromState!!.stateName == curConfiguringToState!!.stateName) {
            throw CannotEnterSelfException(this.toString())
        }
        val key = getKey(
            curConfiguringFromState!!,
            curConfiguringToState!!
        )
        canEnters[key] = canEnter
        val entrances = allEntrances.computeIfAbsent(
            curConfiguringFromState!!
        ) { LinkedList() }
        entrances.add(curConfiguringToState!!)
        return this
    }
    /**
     * Builds the configuration. And this cannot be modified anymore.
     *
     * @return this
     * @throws NoDefaultStateException thrown when the default State hasn't been set yet
     */
    fun build(): AniConfig<TBlock, TBuild> {
        if (defaultState == null) {
            throw NoDefaultStateException(this.toString())
        }
        built = true
        return this
    }
    /**
     * Generates the Animation State Machine object
     *
     * @param block the block of `build`
     * @param build which has the State Machine
     * @return an Animation State Machine
     * @throws HasNotBuiltYetException thrown when this hasn't built yet
     */
    fun gen(block: TBlock, build: TBuild): AniStateM<TBlock, TBuild> {
        if (!built) {
            throw HasNotBuiltYetException(this.toString())
        }
        return AniStateM(this, block, build)
    }
    /**
     * Gets the condition for entering the `to` State from `from`
     *
     * @param from the current State
     * @param to   the next State
     * @return if the key of `form`->`to` exists, return the condition. Otherwise, return null.
     */
    @Nullable
    fun getCanEnter(from: AniState<TBlock, TBuild>, to: AniState<TBlock, TBuild>): ITrigger<TBlock, TBuild>? {
        return canEnters[getKey(from, to)]!!
    }
    /**
     * Gets all possible States that `from` State can enter
     *
     * @param from the current State
     * @return a collection of States
     */
    fun getAllEntrances(from: AniState<TBlock, TBuild>): Collection<AniState<TBlock, TBuild>> {
        return allEntrances.computeIfAbsent(
            from
        ) { LinkedList() }
    }

    class NoDefaultStateException(message: String) : RuntimeException(message)
    class AlreadyBuiltException(message: String) : RuntimeException(message)
    class HasNotBuiltYetException(message: String) : RuntimeException(message)
    class CannotEnterSelfException(message: String) : RuntimeException(message)
    class NoFromStateException(message: String) : RuntimeException(message)
    class NoToStateException(message: String) : RuntimeException(message)
    companion object {
        /**
         * Calculates the key of two ordered States
         *
         * @param from the current State
         * @param to   the next State
         * @return the key
         */
        @JvmStatic
        private fun getKey(from: AniState<*, *>, to: AniState<*, *>): Any {
            return from.hashCode() xor to.hashCode() * 2
        }
    }
}