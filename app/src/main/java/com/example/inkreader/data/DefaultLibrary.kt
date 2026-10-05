package com.example.inkreader.data

import com.example.inkreader.model.Book
import com.example.inkreader.model.Chapter

object DefaultLibrary {

    fun getInitialBooks(): List<Book> = listOf(
        Book(
            id = "hyperion_1",
            title = "Hyperion",
            author = "Dan Simmons",
            coverPattern = 0,
            progressPercent = 34,
            currentChapterIndex = 0,
            currentPageIndex = 0,
            chapters = listOf(
                Chapter(
                    id = "hyp_c1",
                    title = "Chapter 4: The Priest's Tale",
                    content = """The Hegemony Consul sat on the balcony of his ebony spaceship and played Rachmaninoff's Prelude in C-sharp Minor on an ancient but well-maintained Steinway while great, green, saurian things surged and bellowed in the swamps below.

A thunderstorm was brewing to the north. A curtain of bruised-black clouds was rimmed with lightning along a horizon of giant gymnosperms. Closer in, gray clouds of spores drifted across the wetlands like volcanic ash.

The Consul was not thinking of the storm, nor of the saurian behemoths whose mating bellows shook the damp air. He was thinking of the Hegemony and the Shrike Pilgrimage.

The fatline receiver chimed once, its green indicator flashing with urgency. It had been decades since the Consul had received a message on that band. He stopped playing, fingers lingering on the ivory keys, and stepped into the airlock corridor to access the terminal.

The message was from Meina Gladstone, CEO of the Hegemony of Man.

"Consul, you are summoned. The Shrike Temple on Hyperion has begun to shift in time. The Time Tombs are opening. You have been chosen as one of seven pilgrims to return."

The Consul stared at the glowing amber phosphor. Hyperion. The world of dread and mysteries, beyond the web of the WorldWeb. A world governed by no law except the terrifying presence of the Tree of Thorns and the mechanical horror that hunted among the valley of tombs."""
                ),
                Chapter(
                    id = "hyp_c2",
                    title = "Chapter 5: The Soldier's Tale",
                    content = """Fedmahn Kassad had seen battles on twelve worlds, but nothing in the simulator logs prepared him for the valley of the Time Tombs.

The wind here had teeth. It blew backward through the canyons, carrying the scent of ozone and chilled metal. The monoliths rose like frozen gods against a violet sky that knew neither dawn nor sunset.

He adjusted the focal length of his chameleon suit, watching the shimmering heat signatures of his fellow pilgrims. None of them had spoken since they left the barge. Each carried their private curse, their secret reason for walking directly toward death.

Somewhere in the labyrinth ahead, the Shrike was waiting. Not as a god, but as an engine of retribution."""
                )
            )
        ),
        Book(
            id = "dune_1",
            title = "Dune",
            author = "Frank Herbert",
            coverPattern = 1,
            progressPercent = 68,
            currentChapterIndex = 0,
            currentPageIndex = 0,
            chapters = listOf(
                Chapter(
                    id = "dune_c1",
                    title = "Chapter 1: Arrakis Awakening",
                    content = """A beginning is the time for taking the most delicate care that the balances are correct. This every sister of the Bene Gesserit knows.

To begin your study of the life of Muad'Dib, then, take care that you first place him in his time: born in the 57th year of the Padishah Emperor, Shaddam IV. And take most special care that you locate him in his place: the planet Arrakis.

Do not be deceived by the fact that he was born on Caladan and lived his first fifteen years there. Arrakis, the planet known as Dune, is forever his place.

In the week before their departure to Arrakis, when the final scurrying about had reached a nearly unbearable frenzy, an old crone came to visit the mother of the boy, Paul.

It was a warm night at Castle Caladan, and the ancient pile of stone that had served the Atreides family for twenty-six generations bore that cooled-sweat chill it took on before each change of weather.

The old woman was let in by the side door down the vaulted passage by Paul's room and she was allowed a moment to look upon Paul where he lay in his bed."""
                )
            )
        ),
        Book(
            id = "solaris_1",
            title = "Solaris",
            author = "Stanisław Lem",
            coverPattern = 2,
            progressPercent = 12,
            currentChapterIndex = 0,
            currentPageIndex = 0,
            chapters = listOf(
                Chapter(
                    id = "sol_c1",
                    title = "Chapter 1: The Arrival",
                    content = """At 19:00 hours, ship's time, I made my way to the launching station. The technicians around the capsule were working in silence.

The pneumatic cradle swung out, holding the Prometheus. Through the thick quartz glass of the viewing port, the living ocean of Solaris appeared below us: a vast, gelatinous surface that undulated with slow, rhythmic breathing.

It was neither water nor land, but a colloidal consciousness spanning an entire hemisphere.

When the hatch sealed, all mechanical noise from the station was extinguished. I was alone inside the metal sphere, falling toward the alien sea."""
                )
            )
        ),
        Book(
            id = "ishmael_1",
            title = "Ishmael",
            author = "Daniel Quinn",
            coverPattern = 3,
            progressPercent = 85,
            currentChapterIndex = 0,
            currentPageIndex = 0,
            chapters = listOf(
                Chapter(
                    id = "ish_c1",
                    title = "Chapter 1: The Teacher",
                    content = """The classified ad in the personals section was simple enough:

TEACHER seeks pupil. Must have an earnest desire to save the world. Apply in person.

I crumpled the paper in my hand, disgusted. Another charlatan, I thought. Another guru promising enlightenment for the price of your soul or your wallet. Yet, twenty minutes later, I found myself standing before the address listed in the back alley of the commercial district."""
                )
            )
        )
    )
}
