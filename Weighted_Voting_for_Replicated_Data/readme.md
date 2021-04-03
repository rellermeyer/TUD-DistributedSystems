# Weighted Voting for Replicated Data

Authors:
Annabel Oggel,
Erwin Nieuwlaar,
Jaap Blok,
Mantas Zdanavičius

To manage multiple copies of the same data, 
the paper "Weighted Voting for Replicated Data" by Gifford 
proposes an algorithm that can be implemented on top 
of an already existing stable and distributed file system. 
The algorithm assigns a read and write quorum to 
operations in transactions, and a number of votes to each 
copy of a file. These settings allow a moderator to control 
the read and write characteristics of a file in terms of
consistency, availability and performance.

This project implements the Weighted Voting algorithm on a 
simplified file system for a single user.

## Running the project

The application can be run through an `sbt` shell
by using the command `run`. 

Through the UI one of the
following main classes can be selected:

"Main" : Provides a user interface through which an abstacted
file system with weighted voting can be controlled

"Experiment" : Generates runtime statistics in CSV format based on 
the parameters set in the corresponding file