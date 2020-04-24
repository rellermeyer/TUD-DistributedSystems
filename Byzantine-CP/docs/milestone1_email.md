# Milestone 1 [&uarr;](./../README.md)

Email sent (27.02.2020 16:53):  
Subject: **IN4193 Distributed Systems: 1st Milestone for Group 7 (byzantine-cp)**

>Dear Mr. Omranian Khorasani,
>
>With this email I'm sending our action plan regarding implementing the paper about a byzantine fault tolerant distributed commit protocol.
>
>Action plan:
>The commit protocol described in our paper is quite limited in scope, which is why plan to implement most, if not all parts. The implementation will be done in Scala. Our idea is to use [Akka Actors](https://doc.akka.io/docs/akka/current/typed/index.html) for easier communication between the coordinators and participants, we are not going to use Akka Cluster.
>
>Must have:
>
>* Implementation for coordinators
>* Implementation for participants/initiators
>* The whole system must be tested, including different byzantine behaviors of one coordinator.
>
>Should have / Could have:
>
>* Testing our implementation on multiple machines interconnected on a network instead of just locally, using Artery Remoting by Akka (using UDP/Aeron).
>* Implementing signing of messages and checking of the signatures, most likely by having certificates issued by a human-controlled CA.
>
>Could have / Won't have:
>
>* View change mechanisms (these were not implemented by the authors either)
>
>We're looking forward to work on this project and appreciate any feedback.
>
>Kind regards,
>Michael Leichtfried

Email reply (05.03.2020 17:27):  
From: **Sobhan Omranian Khorasani**  
Subject: **Re: IN4193 Distributed Systems: 1st Milestone for Group 7 (byzantine-cp)**

>Dear Michael,
>
>The proposed design seems sound. I would just recommend that during the implementation, also think about how you are going to evaluate the core functionalities of your system. Having a rough idea early always helps in the end.
>
>Regards,
>Sobhan