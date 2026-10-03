package io.kontour.ui.docs.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The icon browser's search: by name, by Tabler's tags, by the synonyms the tags
 * miss, by plural and by one letter wrong, closest first.
 *
 * Asked for as *"if you can include some sort of smart/synonym search (where
 * searching for things like "house" would bring up "home", etc.), that would be
 * awesome"*, over a list that is otherwise *"alphabetically"*.
 *
 * Over real entries: these lines are Tabler 3.48's own, as `docs/icondata.py`
 * lays them out.
 */
class IconSearchTest {

    private val index = requireNotNull(
        IconIndex.parse(
            (listOf("tabler\t3.48.0\t24\t11\t128") + listOf(
            "a-b-2\tText\ttest,visual,user,design\t0",
            "adjustments-cog\tSystem\tsettings,gear,mechanism,machinery,wheel,engine,operation,control,process,system\t0",
            "arrow-big-up\tArrows\tdirection,north,pointer,top,increase,navigation,flow,navigate\t1",
            "arrow-left\tArrows\tback,swipe,return,direction,pointer,navigation,flow,navigate\t0",
            "arrow-up\tArrows\ttop,point,direction,pointer,increase,navigation,flow,navigate,move\t0",
            "arrows-up\tArrows\tdirection,north,top,increase,navigation,flow,rise,upward\t0",
            "auth-2fa\tDevelopment\tlogin,password,verification,code,two-step,programming,software,coding\t0",
            "building\tBuildings\tflat,office,city,urban,scyscraper,architecture,construction,structure,property\t0",
            "check\tSystem\ttick,yes,confirm,control,operation,approve,function,interface,management\t1",
            "cube-3d-sphere\tSystem\tprinting,vector,shape\t0",
            "door\tSystem\tentrance,home,house,room\t0",
            "fence\tBuildings\tgarden,home,house,farm,wood,barrier,architecture,structure,construction\t1",
            "home\tBuildings\thouse,dashboard,living,building,main,architecture,structure,start,construction\t1",
            "home-2\tBuildings\thouse,dashboard,living,building,main,architecture,structure,start,construction\t1",
            "home-check\tBuildings\thouse,tick,mark,assure,safety,main,architecture,structure\t0",
            "number-123\tNumbers\tnumbers,digit,one,two,three,numeric,numeral,count\t0",
            "search\tSystem\tfind,magnifier,magnifying glass,look,seek,query,browse\t1",
            "settings\tSystem\tcog,edit,gear,preferences,tools,config,options,control,operation\t1",
            "settings-2\tSystem\tcog,edit,gear,preferences,tools,config,options,control,operation\t0",
            "trash\tSystem\tgarbage,delete,remove,bin,ash-bin,uninstall,dustbin,control,operation\t1",
            "user\tSystem\tperson,account,control,operation,profile,member,function,interface,management\t1",
            "window\tBuildings\thouse,view,glass,apartment,vehicle,light,frame,home,building\t0",
            "x\tSystem\tcancel,remove,delete,empty,close\t1",
            "zoom\tMap\tfind,magnifier,magnifying glass,location,navigation,magnify,enlarge,scale,focus\t1",
            )).joinToString("\n"),
        ),
    )

    private fun names(query: String) = searchIcons(index.entries, query).map { it.name }

    @Test
    fun nothingTypedListsEveryIconAlphabetically() {
        val all = names("")
        assertEquals(index.entries.size, all.size)
        assertEquals(all.sorted(), all)
    }

    @Test
    fun aHouseIsAHome() {
        val found = names("house")
        assertEquals("home", found.first(), "searching for a house leads with $found")
        assertTrue("window" in found && "door" in found, "the other icons Tabler tags house are missing: $found")
    }

    @Test
    fun theNameItselfComesFirst() {
        assertEquals(listOf("home", "home-2", "home-check"), names("home").take(3))
        assertEquals("home", names("HOME").first(), "case is not folded")
    }

    @Test
    fun aPluralFindsWhatItsSingularDoes() {
        assertEquals(names("house"), names("houses"))
    }

    @Test
    fun everyWordHasToAnswer() {
        val found = names("arrow up")
        assertEquals("arrow-up", found.first())
        assertTrue("arrow-left" !in found, "arrow up found an arrow that is not up: $found")
        assertTrue(found.all { "arrow" in it }, "arrow up found something that is not an arrow: $found")
    }

    @Test
    fun aSynonymTheTagsMissLeadsToItsIcon() {
        assertEquals("settings", names("gear").first(), "a gear is not settings: ${names("gear")}")
        assertEquals("x", names("close").first(), "close is not x: ${names("close")}")
        assertEquals("trash", names("delete").first(), "delete is not trash: ${names("delete")}")
    }

    @Test
    fun aWordTypedWrongStillFindsSomething() {
        val found = names("hosue")
        assertTrue("home" in found && "door" in found, "one letter swapped found $found")
        assertTrue(names("qqqqq").isEmpty())
    }

    @Test
    fun theKotlinNameIsTheGeneratorsRule() {
        fun property(name: String) = index.entries.single { it.name == name }.property
        assertEquals("AB2", property("a-b-2"))
        assertEquals("Auth2fa", property("auth-2fa"))
        assertEquals("Cube3dSphere", property("cube-3d-sphere"))
        assertEquals("Number123", property("number-123"))
        val home = index.entries.single { it.name == "home" }
        assertEquals("Tabler.Filled.Home", home.reference(IconStyle.Filled))
        assertEquals("import io.kontour.icons.tabler.outline.Home", home.import(IconStyle.Outline))
    }

    @Test
    fun oneEditApartIsNearly() {
        assertTrue(nearly("house", "hosue"))
        assertTrue(nearly("house", "hous"))
        assertTrue(nearly("house", "houses"))
        assertTrue(!nearly("house", "horse ") && !nearly("house", "mouse2"))
    }
}
