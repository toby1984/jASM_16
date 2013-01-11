/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmulatorStartStopTest extends AbstractEmulatorTest 
{
    public void testStartStopEmulator() throws InterruptedException, TimeoutException {

        final int TEST_DURATION_SECONDS = 10;
        final int THREAD_COUNT = Runtime.getRuntime().availableProcessors()+1;

        final String source = "label:  ADD A,1\n" +
                "      SET PC,label\n";

        emulator.setOutput( ILogger.NOP_LOGGER );
        execute(source,(TEST_DURATION_SECONDS+2)*1000 , false );

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(THREAD_COUNT);        
        final AtomicBoolean terminate = new AtomicBoolean(false);
        final List<Thread> threads = new ArrayList<>();

        for ( int i = 0 ; i < THREAD_COUNT ;i++) {
            final Thread thread = new Thread("test-thread-"+i) 
            {
                public void run() 
                {
                    try {
                        final Random rnd = new Random(System.currentTimeMillis());
                        try {
                            startLatch.await();
                        } 
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }

                        System.out.println("Thread "+this+" started.");
                        
                        int count = 0;
                        while ( terminate.get() == false ) 
                        {
                            if ( rnd.nextInt(100) > 50 ) {
                                emulator.start();
                            } else {
                                emulator.stop();
                            }
                            
                            count++;
                            if ( (count % 10) == 0 ) {
                                System.out.println( getName() );
                            }
                            
                            try {
                                Thread.sleep(20);
                            } 
                            catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                    } finally {
                        System.out.println("Thread "+this+" terminated");
                        stopLatch.countDown();
                    }
                }
            };
            threads.add( thread );
            thread.start();
        }

        // start all threads
        System.out.println("Starting threads.");
        startLatch.countDown();

        try {
            java.lang.Thread.sleep( TEST_DURATION_SECONDS*1000 );
        } 
        finally {
            System.out.println("Terminating threads...");
            terminate.set( true );
        }
        System.out.println("Waiting for threads to stop");
        stopLatch.await();
        System.out.println("All threads are stopped.");
    }      
}
