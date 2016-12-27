package org.openas2.logging;

public class Level {
    public static final Level FINE = new Level("fine");
    public static final Level FINER = new Level("finer");
    public static final Level FINEST = new Level("finest");
    public static final Level ERROR = new Level("error");
    public static final Level WARNING = new Level("warning");
    private String name;

    public Level(String name) {
        super();
        this.name = name;
    }

    public void setName(String string) {
        name = string;
    }

    public String getName() {
        return name;
    }
}
