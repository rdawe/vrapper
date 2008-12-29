package de.jroene.vrapper.eclipse;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.ITextViewerExtension6;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import de.jroene.vrapper.vim.LineInformation;
import de.jroene.vrapper.vim.Platform;
import de.jroene.vrapper.vim.Space;

/**
 * Eclipse specific implementation of {@link Platform}.
 * There is an instance for every editor with vim functionality added.
 * 
 * @author Matthias Radig
 */
public class EclipsePlatform implements Platform {

    private final IWorkbenchWindow window;
    private final AbstractTextEditor part;
    private final ITextViewer textViewer;
    private final ITextViewerExtension5 textViewerExtension5;
    private final IUndoManager undoManager;
    private final StatusLine statusLine;
    private Space space;

    public EclipsePlatform(IWorkbenchWindow window, AbstractTextEditor part,
            final ITextViewer textViewer) {
        super();
        this.window = window;
        this.part = part;
        this.textViewer = textViewer;
        this.undoManager = getUndoManager();
        this.textViewerExtension5 = textViewer instanceof ITextViewerExtension5
        ? (ITextViewerExtension5) textViewer
                : null;
        setDefaultSpace();
        statusLine = new StatusLine(textViewer.getTextWidget());
    }

    public String getText(int index, int length) {
        try {
            switch (space) {
            case MODEL:
                return textViewer.getDocument().get(index, length);
            case VIEW:
                return textViewer.getTextWidget().getText(index, index+length-1);
            default:
                throw new IllegalStateException("unknown space: " + space);
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    public void replace(int index, int length, String s, boolean setUndoMark) {
        checkForModelSpace("replace()");
        try {
            IDocument doc = textViewer.getDocument();
            if(index > doc.getLength()) {
                index = doc.getLength();
            }
            if (setUndoMark) {
                setUndoMark();
            }
            doc.replace(index, length, s);
        } catch (BadLocationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void setActionLine(String actionLine) {
        // TODO Auto-generated method stub

    }

    public void setCommandLine(String commandLine) {
        statusLine.setContent(commandLine);
    }

    public LineInformation getLineInformation() {
        return getLineInformationOfOffset(getPosition());
    }

    public LineInformation getLineInformation(int line) {
        if(space.equals(Space.VIEW)) {
            line = widgetLine2ModelLine(line);
        }
        try {
            IRegion region = textViewer.getDocument().getLineInformation(line);
            return createLineInformation(line, region);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

    }

    public LineInformation getLineInformationOfOffset(int offset) {
        int line;
        if(space.equals(Space.VIEW)) {
            line = textViewer.getTextWidget().getLineAtOffset(offset);
        } else {
            try {
                line = textViewer.getDocument().getLineOfOffset(offset);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        }
        return getLineInformation(line);
    }

    public int getNumberOfLines() {
        switch(space) {
        case VIEW:
            return textViewer.getTextWidget().getLineCount();
        case MODEL:
            return textViewer.getDocument().getNumberOfLines();
        default:
            throw new IllegalStateException("unknown space: " + space);
        }
    }

    public int getPosition() {
        switch(space) {
        case MODEL:
            return widgetOffset2ModelOffset(textViewer.getTextWidget()
                    .getCaretOffset());
        case VIEW:
            return textViewer.getTextWidget().getCaretOffset();
        default:
            throw new IllegalStateException("unknown space: " + space);
        }
    }

    public void setPosition(int index) {
        int offset;
        switch (space) {
        case MODEL:
            offset = modelOffset2WidgetOffset(index);
            break;
        case VIEW:
            offset = index;
            break;
        default:
            throw new IllegalStateException("unknown space: " + space);
        }
        textViewer.getTextWidget().setCaretOffset(offset);
        textViewer.getTextWidget().showSelection();
    }

    public void toCommandLineMode() {
        statusLine.setEnabled(true);
    }

    public void toInsertMode() {
        setCaretWidth(1);
        statusLine.setEnabled(false);
    }

    public void toNormalMode() {
        GC gc = new GC(textViewer.getTextWidget());
        setCaretWidth(gc.getFontMetrics().getAverageCharWidth());
        gc.dispose();
        statusLine.setEnabled(false);
    }

    public void redo() {
        if(undoManager != null && undoManager.undoable()) {
            undoManager.undo();
        }
    }

    public void undo() {
        if(undoManager != null && undoManager.undoable()) {
            undoManager.undo();
        }
    }

    public void setUndoMark() {
        if(undoManager != null) {
            undoManager.endCompoundChange();
            undoManager.beginCompoundChange();
        }
    }

    public void save() {
        if(part.isDirty()) {
            part.doSave(null);
        }
    }

    public void shift(int line, int lineCount, int shift) {
        int indent = textViewer.getTextWidget().getLineIndent(line);
        int count = indent + shift;
        textViewer.getTextWidget().setLineIndent(line, lineCount, count);
        setUndoMark();
    }

    public void setSpace(Space space) {

        this.space = space;
    }

    public void setDefaultSpace() {
        space = Space.MODEL;
    }

    private IUndoManager getUndoManager() {
        if(textViewer instanceof ITextViewerExtension6) {
            return ((ITextViewerExtension6)textViewer).getUndoManager();
        }
        return null;
    }

    private void setCaretWidth(int width) {
        Caret caret = textViewer.getTextWidget().getCaret();
        caret.setSize(width, caret.getSize().y);
    }

    private void checkForModelSpace(String operation) {
        if(!space.equals(Space.MODEL)) {
            throw new IllegalStateException("Operation "+operation+" allowed in model space only");
        }
    }

    private int widgetOffset2ModelOffset(int caretOffset) {
        return textViewerExtension5 != null
        ? textViewerExtension5.widgetOffset2ModelOffset(caretOffset)
                : caretOffset;
    }

    private int modelOffset2WidgetOffset(int index) {
        return textViewerExtension5 != null
        ? textViewerExtension5.modelOffset2WidgetOffset(index)
                : index;
    }

    private int modelLine2WidgetLine(int line) {
        return textViewerExtension5 != null
        ? textViewerExtension5.modelLine2WidgetLine(line)
                : line;
    }

    private int widgetLine2ModelLine(int line) {
        return textViewerExtension5 != null
        ? textViewerExtension5.widgetLine2ModelLine(line)
                : line;
    }

    private LineInformation createLineInformation(int line, IRegion region) {
        switch(space) {
        case MODEL:
            return new LineInformation(line, region.getOffset(), region.getLength());
        case VIEW:
            return new LineInformation(
                    modelLine2WidgetLine(line),
                    modelOffset2WidgetOffset(region.getOffset()),
                    region.getLength());
        default:
            throw new IllegalStateException("unknown space: " + space);
        }
    }
}