package com.ethercamp.qa.ui;

//import net.coderazzi.filters.gui.AutoChoices;
//import net.coderazzi.filters.gui.TableFilterHeader;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Admin on 21.04.2015.
 */
public class JMyTable<T> extends JTable {
    @FunctionalInterface
    public interface BiConsumer<A1, A2> {
        void consume(A1 a1, A2 a2);
    }
    public class Column {
        public final String name;
        public final Function<T, String> value;
        public Function<T, String> tooltip = null;
        public Function<T, Color> color = null;
        public Function<T, Boolean> bold = null;
        public int minWidth = -1;
        public int maxWidth = -1;
        public TableCellRenderer customRenderer;
        public TableCellEditor customEditor;
        public BiConsumer<T, Object> writer;

        public Column(String name, Function<T, String> value) {
            this.name = name;
            this.value = value;
        }

        public Column withTooltip(Function<T, String> tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Column withColor(Function<T, Color> color) {
            this.color = color;
            return this;
        }

        public Column withBold(Function<T, Boolean> bold) {
            this.bold = bold;
            return this;
        }

        public Column withMinWidth(int w) {
            minWidth = w;
            return this;
        }

        public Column withMaxWidth(int w) {
            maxWidth = w;
            return this;
        }

        public Column withCustomRenderer(TableCellRenderer customRenderer) {
            this.customRenderer = customRenderer;
            return this;
        }

        public Column withCustomEditor(TableCellEditor customEditor) {
            this.customEditor = customEditor;
            return this;
        }

        public Column withDataWriter(BiConsumer<T, Object> writer) {
            this.writer = writer;
            return this;
        }

        public Column newColumn(String name, Function<T, String> value) {
            return JMyTable.this.newColumn(name, value);
        }

        private boolean needRenderer() {return tooltip != null || color != null || bold != null;}

        public JMyTable end() {return JMyTable.this.construct();}
    }

    private class TableModel extends AbstractTableModel{
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return columns.get(columnIndex).value.apply(rows.get(rowIndex));
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column).name;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columns.get(columnIndex).customEditor != null;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Column column = columns.get(columnIndex);
            if (column.writer != null) {
                column.writer.consume(rows.get(rowIndex), aValue);
            }
//            System.out.println("TableModel.setValueAt:" + "aValue = [" + aValue + "], rowIndex = [" + rowIndex + "], columnIndex = [" + columnIndex + "]");;
//            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }

    private class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            row = convertRowIndexToModel(row);

            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Column col = columns.get(column);
            if (col.tooltip != null) {
                String t = col.tooltip.apply(rows.get(row));
                if (t != null) {
                    l.setToolTipText(t);
                }
            }
            if (col.color != null) {
                Color c = col.color.apply(rows.get(row));
                if (c != null) {
                    l.setForeground(c);
                }
            }
            if (col.bold != null) {
                Boolean a = col.bold.apply(rows.get(row));
                if (a != null && a) {
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                }
            }
            return l;
        }
    }

    private List<Column> columns = new ArrayList<>();
    private List<T> rows = Collections.EMPTY_LIST;

    private JMyTable construct() {
        setModel(new TableModel());
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).customRenderer != null) {
                getColumnModel().getColumn(i).setCellRenderer(columns.get(i).customRenderer);
            } else if (columns.get(i).needRenderer()) {
                getColumnModel().getColumn(i).setCellRenderer(new Renderer());
            }
            if (columns.get(i).customEditor != null) {
                getColumnModel().getColumn(i).setCellEditor(columns.get(i).customEditor);
            }
            if (columns.get(i).minWidth >= 0) {
                getColumnModel().getColumn(i).setMinWidth(columns.get(i).minWidth);
            }
            if (columns.get(i).maxWidth >= 0) {
                getColumnModel().getColumn(i).setMaxWidth(columns.get(i).maxWidth);
            }
        }
        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(getModel());
        setRowSorter(sorter);

        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        return this;
    }

//    public JMyTable withTableFilter(Consumer<TableFilterHeader> setuper) {
//        TableFilterHeader filterHeader = new TableFilterHeader(this, AutoChoices.ENABLED);
//        filterHeader.setForeground(Color.RED);
//        filterHeader.setBackground(Color.LIGHT_GRAY);
//        filterHeader.setFont(filterHeader.getFont().deriveFont(Font.BOLD));
//        if (setuper != null) {
//            setuper.accept(filterHeader);
//        }
//        return this;
//    }

    public Column newColumn(String name, Function<T, String> value) {
        Column c = new Column(name, value);
        columns.add(c);
        return c;
    }

    protected boolean equals(T t1, T t2) {
        return t1 == t2;
    }

    protected void updateTableData(List<T> newRows) {
//        int oRow = 0, nRow = 0;
//        int start = 0, end = 0;
//        while(oRow < rows.size() && nRow < newRows.size()) {
//            start = oRow;
//            while (equals(rows.get(oRow), newRows.get(nRow))) {
//                oRow++;
//                nRow++;
//                end++;
//            }
//            if (end > start) {
//                ((TableModel) getModel()).fireTableRowsUpdated(start, end - 1);
//            }
//
//            if (oRow + 1 < rows.size() && equals(rows.get(oRow + 1), newRows.get(nRow))) {
//                oRow++;
//                ((TableModel) getModel()).fireTableRowsDeleted(oRow, oRow);
//                continue;
//            }
//        }
        List<T> selected = getSelected();
        rows = newRows;
        ((TableModel) getModel()).fireTableDataChanged();
        for (int i =0; i < rows.size(); i++) {
            for (T t : selected) {
                if (equals(t, rows.get(i))) {
                    addRowSelectionInterval(convertRowIndexToView(i), convertRowIndexToView(i));
                    break;
                }
            }
        }
    }

    public void update(List<T> rows) {
//        this.rows = rows;
//        SwingUtilities.invokeLater(() -> ((TableModel) getModel()).fireTableDataChanged());
        SwingUtilities.invokeLater(() -> updateTableData(rows));
    }

    public List<T> getSelected() {
        return IntStream.of(getSelectedRows()).mapToObj(i -> rows.get(convertRowIndexToModel(i))).collect(Collectors.toList());
    }

    public T getRow(int row) {
        return rows.get(row);
    }

    public List<T> getRows() {
        return rows;
    }
}
