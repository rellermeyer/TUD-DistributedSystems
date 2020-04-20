# Other Stuff [&uarr;](./../README.md)

## Progress

* Decide for a paper
* Initial design
* Set up a GitHub repository
* Getting scala + akka + IDE up and running
* Implement the basic functionality
* view changes
* timeouts
* signing&certificates
* running in a distributed fashion (multiple computers)
* adding meaningful payload to transactions for testing
* thinking about how to evaluate
* implementing code for evaluation & evaluate
* report writing
* report review
* submission :)

## Questions to be answered

* (some images like state diagram similar to WS-AT) (ONE image added so far)
* image of our system structure (? including what messages are sent and a small number that explains in what order they are sent)
* What are the big differences to WS-AT (Partialy done)
* What was our additional plan?
* What steps did we take to implement it / how often and why have we changed our plan?
* Why have we implemented it the way it is implemented?
* What would we do different
* What is working well in our implementation
* What is working not so well in our implementation
* compared to the paper, what have we left out?
* If we did more than the paper, what?
* did we decide to do anything different than the paper?
* discussion of usecases for a (BFT) commit protocol     (Partial done)
* listing a few different ways how we could evaluate our system (e.g. performance) and some short discussion

## Milestone 2

>Dear All,
>
>Please remember that the deadline for the 2nd milestone of the group project (implementation) is March 20th. 
>
>At this stage, most of the core functionalities that you identified in the previous step should be implemented. The next step is to think about how your system can be evaluated. Please send me your codebase along side a short report that discusses how you are going to evaluate the implemented system. (e.g., what metrics, what is the setup, which experiments, etc.)
>
>Regards,
>
>Sobhan

## Difficulties

* remote, corona
* difficult for 4 persons to work on the same 2 or 3 main files

## Questions to the paper

The decision certificate contains a list of votes and registrations - both are signed by the sender. Why does the signature for the vote not include the sender? We assume that this is a typo in the paper.

p.39 "Furthermore, we assume that a correct participant *registers with f+1 or more correct coordinator replicas* before it sends a reply to the initiator when the transaction is propagated to this participant with a request coming from the initiator."  
p.42 "Because the participant p is correct and responded to the initiator's request properly, it must have *registered with at lease 2f+1 coordinator replicas* prior to sending its reply to the initiator."  
-- The number of registrations is the *same* as the first specifically mentions *correct* coordinator replicas. Therefore the participant actually has to register with f more replicas.

Initially it was not clear that the initiator propagates the transaction to all participants, as the Introduction specifically mentions the participants-have-to-know-all-other-participants as a drawback of another protocol.

"A backup suspects the primary and initiates a view change immediately if the ba-pre-prepare message fails the verification"  
-- shouldn't the view-changes be voted on?

"When the primary for view v+1 receives 2f+1 valid view change messages[...], it [...] multicasts a new view message for view v+1."
-- what if the new primary is byzantine (and does not send out the new view), how is it guaranteed that another replica takes over to view v+2

Initially it was not clear whether the initiator should send the commit request to the primary coordinator only.

## Insights

"The initiator is regarded as a special participant" -> The initiator will also receive the commits/aborts by the coordinators.

## Implementation

## Thoughts

It seems to be somewhat careless that the paper authors have not implemented view changes. We therefore assume that no full implementation of this protocol exists up to now.

Pseudo-code: The paper never mentions if the functions are thought to be executed on coordinator or participant side.

The paper mentions WS-AT a few times, but they have made it more clear that it that they assume strong knowledge of WS-AT. Reading WS-AT helped a lot!

Implementation: As we're implementing a commit protocol which is based on messages, it makes sense to use a framework for passing messages. As we are restricted to Scala and akka seems to be one of the most-used frameworks (actor framework) for that purpose, we chose to use that. We decided against directly implementing participants and coordinators as a FSM as our team is more familiar with more imperative programming. Furthermore, in the beginning we were not sure if we understood all parts of the paper.

### About running it in a distributed fashion

The idea to get our implementation running in a distributed fashion is:

* Manually start akka actors in different JVMs (could be on the same or on different PCs)
* Get the actors to communicate with each other using Artery (serialization of messages, actor discovery)
* Key distribution might be hard, disable the checks in code

## How-To create a pdf report

A pdf-file can be generated by using pandoc and the provided template[^1]. The command:

    pandoc report.md -o report.pdf --from markdown --template .pandoc/templates/eisvogel.latex --listings

## Additional features

Implementing view changes is something we're working on.  

## Footnotes

[^1]: The template is based on [eisvogel (Latex)](https://github.com/Wandmalfarbe/pandoc-latex-template)
