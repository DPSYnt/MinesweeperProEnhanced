package com.minesweeper.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

@SuppressWarnings("serial")
public final class RulesPanel extends JPanel {
    public RulesPanel() {
        super(new BorderLayout(0, 8));
        setBackground(UiTheme.BACKGROUND);
        add(new RuleDiagram(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("基本规则", page(RuleBook.quickHtml()));
        tabs.addTab("难度配置", page(RuleBook.difficultyHtml()));
        tabs.addTab("特殊地雷", page(RuleBook.specialHtml()));
        add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(820, 650));
    }

    private static JScrollPane page(String html) {
        JEditorPane editor = new JEditorPane("text/html", html);
        editor.setEditable(false);
        editor.setBackground(UiTheme.SURFACE);
        editor.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        return scroll;
    }
}
