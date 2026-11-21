package com.minesweeper;

import com.minesweeper.ui.MinesweeperFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** 唯一应用入口，所有Swing组件在EDT上创建。 */
public final class MinesweeperApplication {
    private MinesweeperApplication() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                     | UnsupportedLookAndFeelException exception) {
                System.getLogger(MinesweeperApplication.class.getName()).log(
                        System.Logger.Level.WARNING, "无法使用系统外观，采用默认外观。", exception);
            }
            MinesweeperFrame.launch();
        });
    }
}
