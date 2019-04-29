# Gia
The Gia implementation provided aims to implement a Gnutella-like P2P system as introduced in the paper "Making Gnutella-like P2P Systems Scalable" by Chawathe et al (2003). Our implementation was created in Scala and aims to act as a testbed for emulation of their proposals.

This Gia implementation was developed by David Alderliesten, Ruben Bes, Martijn de Heus, and Philippe Misteli.

# Instructions
The Gia implementation aims to work as a testbed for experimentation, and relies on Maven for dependency management. Import the `POM` file and the project and its associated dependencies should be loaded for you.

To execute the program, you can utilize one of three main methods:

1. Using `App.scala` allows for general execution of the program with a simple sequential sequence.
2. Using `FeatureTestNActors.scala` allows a test to be done with the `N` defined actors.
3. For comparison, using `FeatureTestNActorsGntuella.scala` does the same as `2`, except in Gnutella.

# Experimental Results
The experimental results found by our work can be found within the Experiments folder.
