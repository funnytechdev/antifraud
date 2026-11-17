package com.antifraud.generator.publish;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Delivers the blacklist first, then starts the transaction stream. */
@Component
class GeneratorRunner implements ApplicationRunner {

    private final BlacklistFeed blacklistFeed;
    private final TransactionEmitter emitter;

    GeneratorRunner(BlacklistFeed blacklistFeed, TransactionEmitter emitter) {
        this.blacklistFeed = blacklistFeed;
        this.emitter = emitter;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        blacklistFeed.publish();
        emitter.start();
    }
}
