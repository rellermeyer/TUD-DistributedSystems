"use strict";
const request = require('request-promise');
const fs = require('fs');
const child_process = require('child_process');
const rimraf = require("rimraf");

const PUT_ULR = "http://localhost:9001";
const GET_ULR = "http://localhost:9011";
const CONCURRENT = 100;
const fetch = require('node-fetch');

global.fetch = fetch;


async function sleep(mili) {
    return new Promise((resolve => {
        setTimeout(resolve, mili);
    }));
}

function sum(ar) {
    let s = 0;

    for (let a of ar) {
        s += a;
    }

    return s;
}

function avgF(ar) {
    return sum(ar) / ar.length;
}

function std(ar) {
    let avg = avgF(ar);
    let std = 0;

    for (let a of ar) {
        std += Math.pow(a - avg, 2);
    }

    return {std: Math.sqrt(std / ar.length), avg}
}


async function loadData(url, number) {
    var all = [];
    for (let i = 0; i < number; i++) {
        // var formData = new FormData();

        // formData.append('file', new Blob(['Hello World!\n']), '' + i);

        let formData = {
            file: {value: fs.createReadStream(__dirname + "/file.txt"), options: {filename: '' + i + ".txt"}}
        };

        try {
            all.push(request.put({url, formData: formData}));
        } catch (e) {
            console.error(e);
        }
    }

    await Promise.all(all);
}


async function timedGet(url, number) {
    let t = [];
    let all = [];
    for (let i = 0; i < number; i++) {

        all.push(new Promise(async (resolve) => {
            const start = Date.now();
            try {
                let p = await fetch(url + "/" + i + ".txt");
                let body = await p.text();

                t.push(Date.now() - start);
                resolve();
            } catch (e) {
                console.error(e);
            }
        }));

    }

    await Promise.all(all);
    return std(t);
}

async function run() {
    for (let CONCURRENT of [1, 2, 3, 4, 5, 10, 20, 30, 50, 70, 100]) {
        for (let nodeNr of [2, 3, 4, 5, 10, 20]) {
            let p = child_process.exec("/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/bin/java \"-javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=61645:/Applications/IntelliJ IDEA.app/Contents/bin\" -Dfile.encoding=UTF-8 -classpath /Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/deploy.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/javaws.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/jfxswt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/management-agent.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/plugin.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/jre/lib/rt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/ant-javafx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/dt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/javafx-mx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/jconsole.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/packager.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/sa-jdi.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/lib/tools.jar:/Users/mihaivo/scala/practi/target/scala-2.12/classes:/Users/mihaivo/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.12.8.jar:/Users/mihaivo/.ivy2/cache/org.scala-lang.modules/scala-parser-combinators_2.12/bundles/scala-parser-combinators_2.12-1.1.1.jar:/Users/mihaivo/.ivy2/cache/org.scala-lang.modules/scala-java8-compat_2.12/bundles/scala-java8-compat_2.12-0.8.0.jar:/Users/mihaivo/.ivy2/cache/org.reactivestreams/reactive-streams/jars/reactive-streams-1.0.2.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-stream_2.12/jars/akka-stream_2.12-2.5.21.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-protobuf_2.12/jars/akka-protobuf_2.12-2.5.21.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-actor_2.12/jars/akka-actor_2.12-2.5.21.jar:/Users/mihaivo/.ivy2/cache/com.typesafe/ssl-config-core_2.12/bundles/ssl-config-core_2.12-0.3.7.jar:/Users/mihaivo/.ivy2/cache/com.typesafe/config/bundles/config-1.3.3.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-http-core_2.12/jars/akka-http-core_2.12-10.1.7.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-http_2.12/jars/akka-http_2.12-10.1.7.jar:/Users/mihaivo/.ivy2/cache/com.typesafe.akka/akka-parsing_2.12/jars/akka-parsing_2.12-10.1.7.jar:/Users/mihaivo/.ivy2/cache/org.scala-lang/scala-reflect/jars/scala-reflect-2.12.8.jar:/Users/mihaivo/.ivy2/cache/org.scalactic/scalactic_2.12/bundles/scalactic_2.12-3.0.5.jar:/Users/mihaivo/.ivy2/cache/org.scalaj/scalaj-http_2.12/jars/scalaj-http_2.12-2.4.1.jar cord " + nodeNr);
            await sleep(5000);
            await loadData(PUT_ULR, CONCURRENT);
            await sleep(100);
            let results = await timedGet(GET_ULR, CONCURRENT);
            results.requests = CONCURRENT;
            results.nodeNr = nodeNr;

            console.log(results);

            await p.kill();

            rimraf.sync(__dirname + "./cord-run");
        }

    }
}

run();
