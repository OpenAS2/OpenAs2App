package org.openas2.app.message;

import org.junit.jupiter.api.Test;
import org.openas2.app.message.ListMessagesCommand.DateRange;
import org.openas2.app.message.ListMessagesCommand.InvalidRangeException;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the from/to/fromTime/toTime resolution matrix in ListMessagesCommand, independent of
 * any DB/session so it can run as a fast, pure-logic unit test.
 */
public class ListMessagesCommandDateRangeTest {

    private final ListMessagesCommand command = new ListMessagesCommand();

    private HashMap<String, String> filters(String... kvPairs) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    @Test
    public void noParamsIsFullyUnbounded() throws Exception {
        DateRange range = command.resolveDateRange(filters());
        assertNull(range.from);
        assertNull(range.to);
    }

    @Test
    public void fromOnlyIsOpenEnded() throws Exception {
        DateRange range = command.resolveDateRange(filters("from", "2026-06-11"));
        assertEquals(Timestamp.valueOf("2026-06-11 00:00:00"), range.from);
        assertNull(range.to);
    }

    @Test
    public void toOnlyIsOpenStarted() throws Exception {
        DateRange range = command.resolveDateRange(filters("to", "2026-06-11"));
        assertNull(range.from);
        assertEquals(Timestamp.valueOf("2026-06-11 23:59:59"), range.to);
    }

    @Test
    public void fromAndToWithNoTimesUseWholeDayDefaults() throws Exception {
        DateRange range = command.resolveDateRange(filters("from", "2026-06-11", "to", "2026-06-12"));
        assertEquals(Timestamp.valueOf("2026-06-11 00:00:00"), range.from);
        assertEquals(Timestamp.valueOf("2026-06-12 23:59:59"), range.to);
    }

    @Test
    public void fromTimeOnlyDefaultsToEndOfToDay() throws Exception {
        DateRange range = command.resolveDateRange(
                filters("from", "2026-06-11", "to", "2026-06-12", "fromTime", "08:30:00"));
        assertEquals(Timestamp.valueOf("2026-06-11 08:30:00"), range.from);
        assertEquals(Timestamp.valueOf("2026-06-12 23:59:59"), range.to);
    }

    @Test
    public void toTimeOnlyDefaultsToStartOfFromDay() throws Exception {
        DateRange range = command.resolveDateRange(
                filters("from", "2026-06-11", "to", "2026-06-12", "toTime", "17:45:00"));
        assertEquals(Timestamp.valueOf("2026-06-11 00:00:00"), range.from);
        assertEquals(Timestamp.valueOf("2026-06-12 17:45:00"), range.to);
    }

    @Test
    public void bothTimesAreApplied() throws Exception {
        DateRange range = command.resolveDateRange(
                filters("from", "2026-06-11", "to", "2026-06-12", "fromTime", "08:30:00", "toTime", "17:45:00"));
        assertEquals(Timestamp.valueOf("2026-06-11 08:30:00"), range.from);
        assertEquals(Timestamp.valueOf("2026-06-12 17:45:00"), range.to);
    }

    @Test
    public void fromAndFromTimeWithNoToDefaultsToNow() throws Exception {
        long before = System.currentTimeMillis();
        DateRange range = command.resolveDateRange(filters("from", "2026-06-11", "fromTime", "08:30:00"));
        long after = System.currentTimeMillis();

        assertEquals(Timestamp.valueOf("2026-06-11 08:30:00"), range.from);
        assertNotNull(range.to);
        assertTrue(range.to.getTime() >= before && range.to.getTime() <= after,
                "Expected 'to' to be resolved to roughly now");
    }

    @Test
    public void fromFromTimeAndToTimeWithNoToIsAnError() {
        InvalidRangeException e = assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters("from", "2026-06-11", "fromTime", "08:30:00", "toTime", "17:45:00")));
        assertTrue(e.getMessage().contains("toTime"));
    }

    @Test
    public void fromTimeWithNoFromIsAnError() {
        InvalidRangeException e = assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters("to", "2026-06-11", "fromTime", "08:30:00")));
        assertTrue(e.getMessage().contains("fromTime"));
    }

    @Test
    public void toAndToTimeWithNoFromIsOpenStarted() throws Exception {
        DateRange range = command.resolveDateRange(filters("to", "2026-06-11", "toTime", "17:45:00"));
        assertNull(range.from);
        assertEquals(Timestamp.valueOf("2026-06-11 17:45:00"), range.to);
    }

    @Test
    public void fromTimeAloneWithNoDatesIsAnError() {
        assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters("fromTime", "08:30:00")));
    }

    @Test
    public void toTimeAloneWithNoDatesIsAnError() {
        assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters("toTime", "17:45:00")));
    }

    @Test
    public void malformedDateThrowsParseException() {
        assertThrows(ParseException.class, () ->
                command.resolveDateRange(filters("from", "not-a-date")));
    }

    @Test
    public void malformedTimeThrowsParseException() {
        assertThrows(ParseException.class, () ->
                command.resolveDateRange(filters("from", "2026-06-11", "fromTime", "not-a-time")));
    }

    @Test
    public void invertedTimesOnSameDayIsAnError() {
        InvalidRangeException e = assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters(
                        "from", "2026-06-11", "to", "2026-06-11",
                        "fromTime", "18:00:00", "toTime", "08:00:00")));
        assertTrue(e.getMessage().contains("start time cannot be after the end time"));
    }

    @Test
    public void invertedWholeDateRangeIsAnError() {
        InvalidRangeException e = assertThrows(InvalidRangeException.class, () ->
                command.resolveDateRange(filters("from", "2026-07-01", "to", "2026-06-01")));
        assertTrue(e.getMessage().contains("start time cannot be after the end time"));
    }
}
