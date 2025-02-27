/*
 * DiffBufferHandler - A diff-based buffer change handler.
 *
 * Copyright (C) 2009 Shlomy Reinstein
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package lcm.providers.diff;

import java.awt.Color;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Map.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdiff.util.Diff;
import jdiff.util.Diff.Change;

import lcm.BufferHandler;
import lcm.LCMPlugin;
import lcm.painters.ColoredRectWithStripsPainter;
import lcm.painters.DirtyMarkPainter;
import lcm.providers.diff.Range.ChangeType;
import lcm.XSymbolSubst;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;

import org.gjt.sp.util.Log;

public class DiffBufferHandler extends BufferAdapter implements BufferHandler
{
	private Buffer buffer;
	private TreeMap<Range, Range> ranges;
	private Vector<Integer> removedRanges;
	private HashMap<Integer, String> changedLines;
	private TreeMap<Integer, Integer> movedLines;
	private ColoredRectWithStripsPainter painter;

	public DiffBufferHandler(Buffer buffer)
	{
		this.buffer = buffer;
		ranges = new TreeMap<Range, Range>();
		removedRanges = new Vector<Integer>();
		changedLines = new HashMap<Integer, String>();
		movedLines = new TreeMap<Integer, Integer>();
		painter = new ColoredRectWithStripsPainter();
	}

	@Override
	public void contentInserted(JEditBuffer buffer, int startLine, int offset,
		int numLines, int length)
	{
		handleContentChange(startLine, numLines, ChangeType.ADDED);
	}

	@Override
	public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
		int numLines, int length)
	{
		handleContentChange(startLine, 0 - numLines, ChangeType.REMOVED);
	}

	private void handleContentChange(int startLine, int numLines,
		ChangeType change)
	{
		if (buffer.isUntitled())
			return;
		if (numLines == 0)
			addLine(startLine, change);
		else
			doDiff();
		LCMPlugin.getInstance().repaintAllTextAreas();
	}

	private void doDiff()
	{
		clearAllMarks();
		int nBuffer = buffer.getLineCount();
		String [] bufferLines = new String[nBuffer];
		for (int i = 0; i < nBuffer; i++)
			bufferLines[i] = buffer.getLineText(i);

		String[] fileLines = null;

		String encoding = buffer.getStringProperty(buffer.ENCODING);
		if (encoding.equals("UTF-8-Isabelle")) {
			// if we are diffing an Isabelle theory file, convert xsymbols
			// since they are already converted in the buffer
			try {
				String text = new String(Files.readAllBytes(Paths.get(buffer.getPath())));
				fileLines = XSymbolSubst.xsymbolToUnicodeLines(text);
			} catch (IOException e) {
				return;
			}
		} else {
			fileLines = LCMPlugin.getInstance().readFile(buffer.getPath());
			if (fileLines == null)
				return;
		}

		Diff diff = new Diff(fileLines, bufferLines);
		Change edit = diff.diff_2();
		for (; edit != null; edit = edit.next)
		{
			if ((edit.lines0 > 0) && (edit.lines1 == 0))
				removedRanges.add(Integer.valueOf(edit.first1 - 1));
			else
			{
				ChangeType type;
				if ((edit.lines1 > 0) && (edit.lines0 == 0))
					type = ChangeType.ADDED;
				else
					type = ChangeType.CHANGED;
				Range range = new Range(edit.first1, edit.lines1, type);
				ranges.put(range, range);
			}
			movedLines.put(Integer.valueOf(edit.first1),
					Integer.valueOf(edit.first0));
		}
	}

	public void addLine(int startLine, ChangeType change)
	{
		Integer line = Integer.valueOf(startLine);
		String prev = changedLines.get(line);
		if ((! buffer.isLoaded()) || (buffer.getLineCount() <= startLine))
			return;
		String current = buffer.getLineText(startLine);
		if (prev == null)
		{
			String [] fileLines = LCMPlugin.getInstance().readFile(buffer.getPath());
			if (fileLines == null)
				return;
			Entry<Integer, Integer> movedLine = movedLines.ceilingEntry(line);
			int origLine = startLine;
			if (movedLine != null)
				origLine = movedLine.getValue().intValue() +
					(startLine - movedLine.getKey().intValue());
			if (origLine < fileLines.length)
			{
				prev = fileLines[origLine];
				changedLines.put(line, prev);
			}
		}
		if (prev != null)
			change = (! prev.equals(current)) ? ChangeType.CHANGED : ChangeType.NONE;
		Range range = new Range(startLine, 1, change);
		ranges.put(range, range);
	}

	private void clearAllMarks()
	{
		ranges.clear();
		removedRanges.clear();
		changedLines.clear();
		movedLines.clear();
	}

	public void bufferSaved(Buffer buffer)
	{
		clearAllMarks();
		LCMPlugin.getInstance().repaintAllTextAreas();
	}

	public DirtyMarkPainter getDirtyMarkPainter(Buffer buffer, int physicalLine)
	{
		Range r = ranges.get(new Range(physicalLine, 1));
		boolean removedAbove = removedRanges.contains(Integer.valueOf(physicalLine - 1));
		boolean removedBelow = removedRanges.contains(Integer.valueOf(physicalLine));
		if ((r == null) && (! removedAbove) && (! removedBelow))
			return null;
		painter.setParts(removedAbove, (r != null), removedBelow);
		if (r != null)
		{
			Color c;
			if (r.type == ChangeType.ADDED)
				c = DiffOptions.getAddColor();
			else if (r.type == ChangeType.REMOVED)
				c = DiffOptions.getRemoveColor();
			else if (r.type == ChangeType.CHANGED)
				c = DiffOptions.getChangeColor();
			else
				return null;
			painter.setColor(c);
		}
		return painter;
	}

	public void start()
	{
	}

}
