package cli;

import java.util.List;

public class TableUtils {

    public static void printTable(String[] columns, List<String[]> rows) {
        if (columns == null || rows == null)
            return;

        int[] widths = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            widths[i] = columns[i].length();
        }

        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (row[i] != null && row[i].length() > widths[i]) {
                    widths[i] = row[i].length();
                }
            }
        }

        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            line.append("-").append("-".repeat(width)).append("-+");
        }
        String border = line.toString();

        System.out.println(border);
        printRow(columns, widths);
        System.out.println(border);

        for (String[] row : rows) {
            printRow(row, widths);
        }
        System.out.println(border);
    }

    private static void printRow(String[] row, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < row.length; i++) {
            String val = row[i] == null ? "" : row[i];
            sb.append(" ").append(val);
            sb.append(" ".repeat(widths[i] - val.length()));
            sb.append(" |");
        }
        System.out.println(sb.toString());
    }
}
