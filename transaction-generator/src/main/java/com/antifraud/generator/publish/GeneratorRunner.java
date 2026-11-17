package com.antifraud.generator.publish;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Starts the transaction stream once the application is up. */
@Component
class GeneratorRunner implements ApplicationRunner {

    private final TransactionEmitter emitter;

    GeneratorRunner(TransactionEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void run(ApplicationArguments args) {
        emitter.start();
    }
}
