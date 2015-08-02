package org.openas2.util;

import java.util.Random;


public class RandomUtil {
    private static Random rndGen;

    public static synchronized Random getRandomGenerator() {
        if (rndGen == null) {
            rndGen = new Random();
        }

        return rndGen;
    }
}
