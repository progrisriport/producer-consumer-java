package producerconsumer.tui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import producerconsumer.model.Item;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

/**
 * Righe della schermata del risultato: box dei totali, dei thread e dei duplicati, più la riga
 * di verdetto. Non istanziabile.
 */
final class ResultView {

    static final int MAX_THREAD_ROWS = 8;
    static final int THREAD_COLUMNS = 2;
    static final int MAX_DUPLICATES = 10;
    static final int DUPLICATES_PER_ROW = 5;

    private static final int THREAD_COLUMN_WIDTH = Box.INNER / THREAD_COLUMNS;

    private ResultView() {
    }

    /**
     * @param scenarioName nome dello scenario, titolo del box dei totali
     * @param result       esito del run
     * @return righe dei box; il box dei duplicati compare solo se ci sono duplicati
     */
    static List<String> lines(String scenarioName, SimulationResult result) {
        List<String> lines = new ArrayList<>();
        lines.addAll(Box.box(scenarioName, totals(result)));
        lines.addAll(Box.box("Threads", threads(result)));
        if (!result.getDuplicates().isEmpty()) {
            lines.addAll(Box.box("Duplicates", duplicates(result)));
        }
        return lines;
    }

    /**
     * @return "Every item consumed exactly once." in verde, oppure le cause dell'errore in rosso
     */
    static String verdict(SimulationResult result) {
        List<String> problems = new ArrayList<>();
        if (result.getLost() > 0) {
            problems.add("lost " + result.getLost());
        }
        if (result.getDuplicated() > 0) {
            problems.add("duplicated " + result.getDuplicated());
        }
        if (result.isTimedOut()) {
            problems.add("stopped by timeout");
        }
        if (!result.getNotTerminated().isEmpty()) {
            problems.add(result.getNotTerminated().size() + " threads still running");
        }
        if (problems.isEmpty()) {
            return Theme.success("Every item consumed exactly once.");
        }
        String text = String.join(", ", problems) + ".";
        return Theme.error(Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    private static List<String> totals(SimulationResult result) {
        List<String> body = new ArrayList<>();
        body.add(pair("Produced", String.valueOf(result.getProduced()), "Lost", String.valueOf(result.getLost())));
        body.add(pair("Consumed", String.valueOf(result.getConsumed()),
                "Duplicated", String.valueOf(result.getDuplicated())));
        body.add(pair("Distinct", String.valueOf(result.getDistinct()),
                "Elapsed", Box.seconds(result.getElapsedMillis())));
        return body;
    }

    /** Due coppie etichetta/valore affiancate, ciascuna larga metà del box. */
    private static String pair(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        return Theme.secondary(String.format("%-12s", leftLabel)) + Theme.text(String.format("%10s", leftValue))
                + "      "
                + Theme.secondary(String.format("%-12s", rightLabel)) + Theme.text(String.format("%10s", rightValue));
    }

    private static List<String> threads(SimulationResult result) {
        List<WorkerReport> reports = new ArrayList<>();
        reports.addAll(result.getProducers());
        reports.addAll(result.getConsumers());

        int maxEntries = MAX_THREAD_ROWS * THREAD_COLUMNS;
        boolean anyInterrupted = false;
        List<String> body = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        int inRow = 0;
        int shown = 0;
        for (WorkerReport report : reports) {
            if (report.wasInterrupted()) {
                anyInterrupted = true;
            }
            if (shown == maxEntries) {
                continue;
            }
            String mark = " ";
            if (report.wasInterrupted()) {
                mark = "*";
            }
            String entry = String.format("%-12s%8d%s", report.getName(), report.getCount(), mark);
            row.append(Theme.text(entry)).append(Box.repeat(" ", THREAD_COLUMN_WIDTH - entry.length()));
            inRow++;
            shown++;
            if (inRow == THREAD_COLUMNS) {
                body.add(row.toString());
                row = new StringBuilder();
                inRow = 0;
            }
        }
        if (inRow > 0) {
            body.add(row.toString());
        }
        if (reports.size() > maxEntries) {
            body.add(Theme.secondary("... and " + (reports.size() - maxEntries) + " more"));
        }
        if (anyInterrupted) {
            body.add(Theme.secondary("* interrupted"));
        }
        if (!result.getNotTerminated().isEmpty()) {
            String names = String.join(", ", result.getNotTerminated());
            body.add(Theme.error(Box.truncate("Still running: " + names, Box.INNER)));
        }
        return body;
    }

    /** I duplicati sono ordinati per id; ogni voce è "#id xN". */
    private static List<String> duplicates(SimulationResult result) {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(result.getDuplicates().entrySet());
        entries.sort(new Comparator<Map.Entry<Item, Integer>>() {
            @Override
            public int compare(Map.Entry<Item, Integer> a, Map.Entry<Item, Integer> b) {
                return Integer.compare(a.getKey().getId(), b.getKey().getId());
            }
        });

        int columnWidth = Box.INNER / DUPLICATES_PER_ROW;
        List<String> body = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        int inRow = 0;
        int shown = 0;
        for (Map.Entry<Item, Integer> entry : entries) {
            if (shown == MAX_DUPLICATES) {
                break;
            }
            String text = Box.truncate("#" + entry.getKey().getId() + " x" + entry.getValue(), columnWidth - 1);
            row.append(Theme.text(text)).append(Box.repeat(" ", columnWidth - text.length()));
            inRow++;
            shown++;
            if (inRow == DUPLICATES_PER_ROW) {
                body.add(row.toString());
                row = new StringBuilder();
                inRow = 0;
            }
        }
        if (inRow > 0) {
            body.add(row.toString());
        }
        if (entries.size() > MAX_DUPLICATES) {
            body.add(Theme.secondary("... and " + (entries.size() - MAX_DUPLICATES) + " more"));
        }
        return body;
    }
}
