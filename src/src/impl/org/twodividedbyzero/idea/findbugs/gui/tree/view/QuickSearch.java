/**
 * Copyright 2009 Andre Pfeiler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.twodividedbyzero.idea.findbugs.gui.tree.view;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.common.ui.AbstractBar;
import org.twodividedbyzero.idea.findbugs.common.ui.ToolBarButton;
import org.twodividedbyzero.idea.findbugs.common.util.GuiUtil;
import org.twodividedbyzero.idea.findbugs.resources.GuiResources;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.9.29-dev
 */
public abstract class QuickSearch<E> {

	private static final Logger LOGGER = Logger.getInstance(QuickSearch.class.getName());

	private int _searchDelay = 500;
	private boolean _searchActivated;
	private JLayeredPane _layerPane;
	private SearchPopup _popup;
	private RecentSearchesPopup _recentSearchesPopup;
	private JComponent _component;
	private Color _noMatchForeground;
	private KeyListener _keyListener;
	private FocusListener _focusListener;
	private ComponentListener _componentListener;
	private int _cursor = -1;
	private Color _foregroundColor = UIManager.getColor("ToolTip.foreground");  // NON-NLS
	private Color _backgroundColor = new Color(255, 255, 200);
	private Stack<String> _recentSearches;


	protected QuickSearch() {
		_recentSearches = new Stack<String>();
	}


	protected void install(@NotNull final JComponent component) {
		_component = component;
		//_component.setFocusable(true);
		installListeners();
	}


	private void installListeners() {
		if (_componentListener == null) {
			_componentListener = createComponentListener();
		}
		getComponent().addComponentListener(_componentListener);

		if (_keyListener == null) {
			_keyListener = createKeyListener();
		}
		getComponent().addKeyListener(_keyListener);

		if (_focusListener == null) {
			_focusListener = createFocusListener();
		}
		getComponent().addFocusListener(_focusListener);
	}


	protected abstract void uninstallListeners();


	protected abstract int getElementCount();


	protected abstract String convertElementToString(final E element);


	protected abstract List<?> getElementsCache();


	@Nullable
	protected abstract E getElementAt(int index);


	protected abstract void setSelectedElement(final int index);


	protected void setComponent(final JComponent component) {
		_component = component;
	}


	protected SearchPopup createSearchPopup(final String searchText) {
		return new SearchPopup(searchText);
	}


	protected KeyListener createKeyListener() {
		return new KeyAdapter() {
			@Override
			public void keyTyped(final KeyEvent e) {
				keyTypedOrPressed(e);
			}


			@Override
			public void keyPressed(final KeyEvent e) {
				keyTypedOrPressed(e);
			}
		};
	}


	protected FocusListener createFocusListener() {
		return new FocusAdapter() {
			@Override
			public void focusGained(final FocusEvent e) {
				QuickSearch.this._searchActivated = true;
			}


			@Override
			public void focusLost(final FocusEvent focusevent) {
				//final Component component = focusevent.getOppositeComponent();

				/*if(component != null) {
					final Container parent = component.getParent();
					if(parent != null && QuickSearch.NavigationToolBar.class.isInstance(parent)) {
						_component.requestFocus();
						focusevent.setSource(_component);
						focusGained(focusevent);
						return;
					}
				}*/

				QuickSearch.this._searchActivated = false;
				hidePopup();

			}
		};
	}


	protected ComponentListener createComponentListener() {
		return new ComponentAdapter() {
			@Override
			public void componentHidden(final ComponentEvent e) {
				super.componentHidden(e);
				hidePopup();
			}


			@Override
			public void componentResized(final ComponentEvent e) {
				super.componentResized(e);
				relocateAndResize();
			}


			@Override
			public void componentMoved(final ComponentEvent e) {
				super.componentMoved(e);
				relocateAndResize();
			}
		};
	}


	protected void keyTypedOrPressed(final KeyEvent e) {
		if (e != null && (isActivationKey(e) || _searchActivated) && !isDeactivationKey(e)) {
			String searchingText = "";
			if (e.getID() == KeyEvent.KEY_TYPED) {
				if (((e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0)) { // alt mask
					return;
				}
				if (e.isAltDown()) {
					return;
				}

				searchingText = String.valueOf(e.getKeyChar());
			}

			if (_popup == null) {
				showPopup(searchingText);
			}

			if (e.getKeyCode() != KeyEvent.VK_ENTER) {
				_popup.processKeyEvent(e);
				//_popup.getSearchField().processKeyEvent(e);
				e.consume();
			}
		}
	}


	private void showPopup(final String text) {
		//final JRootPane rootPane = _component.getRootPane();
		final JComponent component = (JComponent) GuiUtil.getScrollPane(_component);
		if (component != null) {
			_component = component;
		}

		final JRootPane rootPane = SwingUtilities.getRootPane(component);
		if (rootPane != null) {
			_layerPane = rootPane.getLayeredPane();
			_popup = createSearchPopup(text);
			_layerPane.add(_popup, JLayeredPane.POPUP_LAYER);
			relocateAndResize();
			_popup.setVisible(true);
			_popup.validate();

			if (!_recentSearches.isEmpty()) {
				showRecentSearchesPopup();
			} else {
				initRecentSearchesPopup(false);
			}
		} else {
			//noinspection AssignmentToNull
			_layerPane = null;
		}

	}


	@SuppressWarnings({"AssignmentToNull"})
	protected void hidePopup() {
		if (_layerPane != null && _popup != null) {
			hideRecentSearchesPopup();
			//final Rectangle bounds = _popup.getBounds();
			_layerPane.remove(_popup);
			//_layerPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
			_layerPane.validate();
			_layerPane.repaint();
			_layerPane = null;
			_popup = null;
			setCursor(-1);
			//_component.requestFocusInWindow();
		}
	}


	private Point relocateAndResize() {
		final Point componentLocation;
		final Dimension popupSize;
		if (_popup != null) {
			try {
				popupSize = _popup.getPreferredSize();
				componentLocation = _component.getLocationOnScreen();
				SwingUtilities.convertPointFromScreen(componentLocation, _layerPane);
				componentLocation.y -= popupSize.height;
				if ((componentLocation.y < 0)) {
					componentLocation.y = 0;
				}
			} catch (IllegalComponentStateException ignore) {
				return null;
			}

			_popup.setLocation(componentLocation);
			_popup.setSize(popupSize);

			return componentLocation;
		} else {
			return null;
		}
	}


	protected static boolean isNavigationKey(final KeyEvent e) {
		return isFindNextOccurenceKey(e) || isFindPreviousOccurenceKey(e);
	}


	protected static boolean isFindNextOccurenceKey(final KeyEvent e) {
		return e.getKeyCode() == KeyEvent.VK_F3;
	}


	protected static boolean isFindPreviousOccurenceKey(final KeyEvent e) {
		return e.getKeyCode() == KeyEvent.VK_F3 && e.getModifiersEx() == KeyEvent.SHIFT_DOWN_MASK;
	}


	protected static boolean isActivationKey(final KeyEvent e) {
		final char keyChar = e.getKeyChar();
		return e.getID() == KeyEvent.KEY_TYPED && e.getKeyCode() != KeyEvent.VK_F4 && e.getModifiersEx() != KeyEvent.ALT_MASK && e.getModifiersEx() != KeyEvent.ALT_DOWN_MASK && (Character.isLetterOrDigit(keyChar) || keyChar == '*' || keyChar == '?');
	}


	protected static boolean isDeactivationKey(final KeyEvent e) {
		final int keyCode = e.getKeyCode();
		return keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN || keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END || keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
	}


	protected int getSearchDelay() {
		return _searchDelay;
	}


	protected void setSearchDelay(final int searchDelay) {
		_searchDelay = searchDelay;
	}


	protected Component getComponent() {
		return _component;
	}


	protected boolean isPopupVisible() {
		return _popup != null;
	}


	protected int getCursor() {
		return _cursor;
	}


	protected void setCursor(final int cursor) {
		_cursor = cursor;
	}


	protected Color getNoMatchForeground() {
		if (_noMatchForeground == null) {
			return Color.RED;
		} else {
			return _noMatchForeground;
		}
	}


	protected Color getForegroundColor() {
		return _foregroundColor;
	}


	protected void setForegroundColor(final Color foregroundColor) {
		_foregroundColor = foregroundColor;
	}


	protected Color getBackgroundColor() {
		return _backgroundColor;
	}


	protected void setBackgroundColor(final Color backgroundColor) {
		_backgroundColor = backgroundColor;
	}


	private int find(final String text) {
		final int count = getElementCount();
		if (count == 0) {
			return -1;
		}

		// find from cursor
		int cursor = getCursor();
		if (cursor == -1 && count > 0) {
			cursor = 0;
			setCursor(cursor);
		}

		for (int i = cursor; i < count; i++) {
			final E element = getElementAt(i);
			if (compare(element, text)) {
				addToRecentSearches(text);
				return i;
			}
		}

		// if not found, search from begin to cursor
		for (int i = 0; i < cursor; i++) {
			final E element = getElementAt(i);
			if (compare(element, text)) {
				//setCursor(i);
				addToRecentSearches(text);
				return i;
			}
		}

		return -1;

	}


	public int findNextOccurence(final String text) {
		final int count = getElementCount();
		if (count == 0) {
			return text.length() > 0 ? -1 : 0;
		}

		for (int i = getCursor() + 1; i < count; i++) {
			final E element = getElementAt(i);
			if (compare(element, text)) {
				return i;
			}
		}

		return -1;
	}


	public int findPreviousOccurence(final String text) {
		final int count = getElementCount();
		if (count == 0) {
			return text.length() > 0 ? -1 : 0;
		}

		for (int i = getCursor() - 1; i >= 0; i--) {
			final E element = getElementAt(i);
			if (compare(element, text)) {
				return i;
			}
		}

		return -1;
	}


	protected boolean compare(final E element, final String searchText) {
		if (searchText == null || searchText.trim().length() == 0) {
			return true;
		}

		final String text = convertElementToString(element);
		if (text == null) {
			return false;
		}

		try {
			//_pattern = Pattern.compile(isFromStart() ? "^" + s : s, isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
			Pattern pattern = Pattern.compile("^" + searchText + "^", 0);
			boolean found = pattern.matcher(text).find();
			if (found) {
				return found;
			}

			pattern = Pattern.compile("^" + searchText, 0);
			found = pattern.matcher(text).find();
			if (found) {
				return found;
			}

			pattern = Pattern.compile("^" + searchText, Pattern.CASE_INSENSITIVE);
			found = pattern.matcher(text).find();
			if (found) {
				return found;
			}

			pattern = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE);
			found = pattern.matcher(text).find();
			return found;
		} catch (PatternSyntaxException ignore) {
			return false;
		}
	}


	private class SearchField extends JTextField {

		SearchField() {
		}


		@Override
		public Dimension getPreferredSize() {
			final Dimension size = super.getPreferredSize();
			size.width = getFontMetrics(getFont()).stringWidth(getText()) + 4;
			return size;
		}


		@Override
		public void processKeyEvent(final KeyEvent e) {

			final int keyCode = e.getKeyCode();
			if (keyCode == KeyEvent.VK_BACK_SPACE && getDocument().getLength() == 0) {
				e.consume();
				return;
			}

			final boolean isNavigationKey = isNavigationKey(e);

			if (isDeactivationKey(e) && !isNavigationKey) {
				hidePopup();
				//_component.setRequestFocusEnabled(true);
				//_component.requestFocusInWindow();
				e.consume();
				return;
			}

			if (keyCode == KeyEvent.VK_BACK_SPACE || isNavigationKey) {
				e.consume();
			}

			super.processKeyEvent(e);
		}


		/*private void handleBackspace() {
			final int caretPos = getCaretPosition();
			final String temp = getText();
			if (caretPos > 1) {
				setText(temp.substring(0, caretPos - 1) + (caretPos < temp.length() ? temp.substring(caretPos, temp.length()) : ""));
			} else {
				setText("" + (caretPos < temp.length() ? temp.substring(caretPos, temp.length()) : ""));
			}
			setCaretPosition(caretPos - 1);
		}*/
	}


	private class SearchPopup extends JPanel {

		private JLabel _label;
		private SearchField _searchField;
		private NavigationToolBar _toolBar;


		public SearchPopup(final String searchText) {
			initGui(searchText);
		}


		private void initGui(final String text) {
			//addKeyListener(_keyListener);

			_label = new JLabel("Search for: ");  // NON-NLS
			_label.setFont(new Font(getFont().getName(), Font.BOLD, 12));
			_label.setForeground(_foregroundColor);  // NON-NLS
			_label.setVerticalAlignment(JLabel.BOTTOM);

			_toolBar = new NavigationToolBar("test", false, SwingConstants.HORIZONTAL);
			_toolBar.setForeground(_foregroundColor);
			_toolBar.setVisible(false);

			_searchField = new SearchField();
			_searchField.setForeground(_foregroundColor);  // NON-NLS
			_searchField.setFocusable(false);
			_searchField.setOpaque(false);
			_searchField.setBorder(BorderFactory.createEmptyBorder());
			_searchField.setCursor(getCursor());

			_searchField.getDocument().addDocumentListener(new DocumentListener() {
				private Timer _timer = new Timer(200, new ActionListener() {
					public void actionPerformed(final ActionEvent e) {
						doFind();
					}
				});


				public void insertUpdate(final DocumentEvent e) {
					startTimer();
				}


				public void removeUpdate(final DocumentEvent e) {
					startTimer();
				}


				public void changedUpdate(final DocumentEvent e) {
					startTimer();
				}


				protected void doFind() {
					final String text = _searchField.getText().trim();
					if (text.length() != 0) {
						final int found = find(text);
						if (found == -1) {
							_noMatchForeground = getNoMatchForeground();
							_searchField.setForeground(_noMatchForeground);
							_toolBar.setVisible(false);
							remove(_toolBar);
							updatePopupBounds();
						} else {
							_searchField.setForeground(_foregroundColor);  // NON-NLS
							_toolBar.setVisible(true);
							add(_toolBar, BorderLayout.LINE_END);
							QuickSearch.this.setCursor(found);
							setSelectedElement(found);
							updatePopupBounds();
						}
					} else {
						hidePopup();
					}
				}


				void startTimer() {
					updatePopupBounds();
					if (getSearchDelay() > 0) {
						_timer.setInitialDelay(getSearchDelay());
						if (_timer.isRunning()) {
							_timer.restart();
						} else {
							_timer.setRepeats(false);
							_timer.start();
						}
					} else {
						doFind();
					}
				}

			});

			_searchField.setText(text);

			setBackground(_backgroundColor);
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY, 1), BorderFactory.createEmptyBorder(1, 6, 1, 6)));
			setLayout(new BorderLayout(2, 0));
			/*final Dimension size = _label.getPreferredSize();
			size.height = _searchField.getPreferredSize().height;
			_label.setPreferredSize(size);

			final Dimension toolBarSize = _toolBar.getPreferredSize();
			toolBarSize.height = _searchField.getPreferredSize().height;*/

			final Dimension size = _label.getPreferredSize();
			size.height = _toolBar.getPreferredSize().height;
			_label.setPreferredSize(size);

			final Dimension sizeField = _searchField.getPreferredSize();
			sizeField.height = size.height;
			_searchField.setPreferredSize(sizeField);

			add(_label, BorderLayout.LINE_START);
			add(_searchField, BorderLayout.CENTER);
		}


		private void updatePopupBounds() {
			if (_popup != null) {
				_searchField.invalidate();
				try {
					final Dimension size = _searchField.getPreferredSize();
					size.width += _label.getPreferredSize().width;
					size.width += _toolBar.isVisible() ? _toolBar.getPreferredSize().width : 0;
					size.width += 20 /*new JLabel(_searchField.getText()).getPreferredSize().width*//* + 19*/;
					size.height = _popup.getSize().height;
					_popup.setSize(size);
					_popup.setPreferredSize(size);
					_popup.validate();
				} catch (Exception ignore) {
				}
			}
		}


		@Override
		public void processKeyEvent(final KeyEvent e) {
			_searchField.processKeyEvent(e);
			if (e.isConsumed()) {
				final String text = getSearchText();

				if (text.length() == 0) {
					return;
				}

				int found = -1;
				boolean foundPrev = true;
				boolean foundNext = true;

				if (isFindNextOccurenceKey(e) && !isFindPreviousOccurenceKey(e)) {
					found = findNextOccurence(text);
					foundNext = found > 1;
				} else if (isFindPreviousOccurenceKey(e)) {
					found = findPreviousOccurence(text);
					foundPrev = found > -1;
				}

				_toolBar.setPrevEnabled(foundPrev);
				_toolBar.setNextEnabled(foundNext);

				if (found == -1) {
					//_searchField.setForeground(getNoMatchForeground());
				} else {
					_searchField.setForeground(UIManager.getColor("Toolip.foreground"));  // NON-NLS
					QuickSearch.this.setCursor(found);
					setSelectedElement(found);
				}
			}
			if (e.getKeyCode() != KeyEvent.VK_ENTER) {
				e.consume();
			}
			super.processKeyEvent(e);
		}


		public String getSearchText() {
			return _searchField.getText();
		}


		public SearchField getSearchField() {
			return _searchField;
		}


		public NavigationToolBar getToolbar() {
			return _toolBar;
		}
	}


	private class NavigationToolBar extends AbstractBar {

		private final Icon _prevIcon = GuiResources.NAVIGATION_MOVEUP_ICON;
		private final Icon _nextIcon = GuiResources.NAVIGATION_MOVEDOWN_ICON;
		private ToolBarButton _prevButton;
		private ToolBarButton _nextButton;


		private NavigationToolBar(final String name, final boolean floatable, final int orientation) {
			super(name);
			setFloatable(floatable);
			setOrientation(orientation);
			setRollover(false);
			initGui();
		}


		private void initGui() {
			setOpaque(false);
			setFocusable(false);
			setRollover(false);
			setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

			final AbstractBar.AbstractComponentAction prevAction = new AbstractComponentAction("Previous Occurence", "Previous Occurence (Ctrl+F3)", KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F3) {
				private static final long serialVersionUID = 0L;


				public void actionPerformed(final ActionEvent e) {
					_popup.processKeyEvent(new KeyEvent(_prevButton, 401, System.currentTimeMillis(), KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F3, KeyEvent.CHAR_UNDEFINED));
				}
			};
			_prevButton = new ToolBarButton(prevAction, _prevIcon);
			add(_prevButton);


			final AbstractBar.AbstractComponentAction nextAction = new AbstractComponentAction("Next Occurence", "Next Occurence (Ctrl+F3)", KeyEvent.VK_F3, 0, KeyEvent.VK_F3) {
				private static final long serialVersionUID = 0L;


				public void actionPerformed(final ActionEvent e) {
					_popup.processKeyEvent(new KeyEvent(_nextButton, 401, System.currentTimeMillis(), 0, KeyEvent.VK_F3, KeyEvent.CHAR_UNDEFINED));
				}
			};
			_nextButton = new ToolBarButton(nextAction, _nextIcon);
			add(_nextButton);
		}


		public void setPrevEnabled(final boolean enable) {
			_prevButton.setEnabled(enable);
		}


		public void setNextEnabled(final boolean enable) {
			_nextButton.setEnabled(enable);
		}
	}


	private class RecentSearchesPopup extends JPanel {

		private JLabel _label;
		private DefaultListModel _listModel;
		private JList _list;
		private int _mouseOverIndex;


		private RecentSearchesPopup() {
			initGui();
		}


		private void initGui() {
			_label = new JLabel("Recent Searches");  // NON-NLS
			_label.setPreferredSize(new Dimension(getPreferredSize().width, _label.getPreferredSize().height + 5));
			_label.setForeground(_foregroundColor);
			_label.setBackground(_backgroundColor);
			_label.setOpaque(true);
			_label.setHorizontalAlignment(JLabel.CENTER);
			_label.setVerticalAlignment(JLabel.CENTER);

			_listModel = new DefaultListModel();
			_list = new JList();
			_list.setModel(_listModel);
			_list.setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));
			_list.setAutoscrolls(true);
			_list.setFocusable(false);
			_list.setRequestFocusEnabled(false);

			updateListData(_recentSearches);
			_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

			_list.addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(final MouseEvent e) {
					locationToListIndex(e);
				}
			});

			_list.addListSelectionListener(new ListSelectionListener() {
				public void valueChanged(final ListSelectionEvent e) {
					if (e.getValueIsAdjusting()) {
						return;
					}

					if (_list.getSelectedIndex() > -1) {
						QuickSearch.this._popup.getSearchField().setText((String) _list.getSelectedValue());
					}
				}
			});

			//updatePopupBounds();
			final JScrollPane scrollPane = new JScrollPane();
			scrollPane.getViewport().setView(_list);
			scrollPane.setBorder(null);
			scrollPane.setForeground(_foregroundColor);

			setLayout(new BorderLayout());
			setBackground(_backgroundColor);
			setOpaque(true);
			setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));  // NON-NLS

			add(_label, BorderLayout.NORTH);
			add(scrollPane, BorderLayout.CENTER);
		}


		private void locationToListIndex(final MouseEvent e) {
			final Point p = e.getPoint();
			int overIndex = _list.locationToIndex(p);

			final Rectangle itemRect = _list.getCellBounds(overIndex, overIndex);
			if (!itemRect.contains(p)) {
				overIndex = -1;
			}

			if (overIndex != _mouseOverIndex) {
				_list.setSelectedIndex(overIndex);
				_list.scrollRectToVisible(itemRect);
				_mouseOverIndex = overIndex;
			}
		}


		private void scrollIndexToVisible(final int index) {
			final Rectangle rect = _list.getCellBounds(index, index);
			_list.setSelectedIndex(index);
			_list.scrollRectToVisible(rect);
		}


		@Override
		public Dimension getPreferredSize() {
			return new Dimension(_label.getPreferredSize().width + 60, 200);
		}


		private void updatePopupBounds() {
			if (_popup != null) {
				_list.invalidate();
				try {
					final Dimension size = _list.getPreferredSize();
					//size.width += _list.getPreferredSize().width;
					size.width = new JLabel(getLongestString()).getPreferredSize().width + 4;
					//size.width += 20 /*new JLabel(_searchField.getText()).getPreferredSize().width*//* + 19*/;
					size.height = _list.getSize().height;
					_list.setSize(size);
					_list.setPreferredSize(size);
					_list.validate();
				} catch (Exception e) {
					LOGGER.debug("updatePopupBounds failed.", e);
				}
			}
		}


		public String getLongestString() {
			int maxLen = 0;
			String longest = null;

			for (int i = 0; i < _listModel.size(); i++) {
				final String s = (String) _listModel.elementAt(i);
				if (s.length() > maxLen) {
					maxLen = s.length();
					longest = s;
				}
			}
			return longest;
		}


		void updateListData(final Stack<String> stack) {
			_listModel.removeAllElements();
			final ListIterator<String> iter = stack.listIterator(stack.size());
			while (iter.hasPrevious()) {
				_listModel.addElement(iter.previous());
			}
		}


		@Nullable
		private JList getList() {
			return _list;
		}


	}


	private void showRecentSearchesPopup() {
		//final JRootPane rootPane = _component.getRootPane();
		final JComponent component = (JComponent) GuiUtil.getScrollPane(_component);
		if (component != null) {
			_component = component;
		}

		final JRootPane rootPane = SwingUtilities.getRootPane(component);
		if (rootPane != null) {
			_layerPane = rootPane.getLayeredPane();
			initRecentSearchesPopup(true);
		}

	}


	private void initRecentSearchesPopup(final boolean visible) {
		_recentSearchesPopup = createRecentSearchesPopup();
		_layerPane.add(_recentSearchesPopup, JLayeredPane.POPUP_LAYER);
		relocateAndResizeRecentSearches();
		_recentSearchesPopup.setVisible(visible);
		_recentSearchesPopup.validate();
	}


	protected void hideRecentSearchesPopup() {
		if (_recentSearchesPopup != null) {
			//final Rectangle bounds = _popup.getBounds();
			_layerPane.remove(_recentSearchesPopup);
			//_layerPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
			_layerPane.validate();
			_layerPane.repaint();
			//_layerPane = null;
			//noinspection AssignmentToNull
			_recentSearchesPopup = null;
			setCursor(-1);
		}
	}


	private Point relocateAndResizeRecentSearches() {
		final Point componentLocation;
		final Dimension popupSize;
		if (_recentSearchesPopup != null && _popup != null) {
			try {
				popupSize = _recentSearchesPopup.getPreferredSize();
				componentLocation = _popup.getLocationOnScreen();
				SwingUtilities.convertPointFromScreen(componentLocation, _layerPane);
				componentLocation.y -= popupSize.height + 2;
				if ((componentLocation.y < 0)) {
					componentLocation.y = 2;
				}
			} catch (IllegalComponentStateException ignore) {
				return null;
			}

			_recentSearchesPopup.setLocation(componentLocation);
			_recentSearchesPopup.setSize(popupSize);

			return componentLocation;
		} else {
			return null;
		}
	}


	protected RecentSearchesPopup createRecentSearchesPopup() {
		return new RecentSearchesPopup();
	}


	private void addToRecentSearches(final String text) {
		if (text.length() >= 3) {
			if (_recentSearches.size() >= 30) {
				_recentSearches.remove(_recentSearches.size() - 1);
			}

			if (_recentSearchesPopup != null) {
				if (!_recentSearches.contains(text)) {
					_recentSearches.push(text);
					//Collections.reverse(_recentSearches);
					_recentSearchesPopup.updateListData(_recentSearches);
				}

				final JList jList = _recentSearchesPopup.getList();
				if (jList != null) {
					final DefaultListModel listModel = (DefaultListModel) jList.getModel();
					final int index = listModel.indexOf(text);
					jList.setSelectedIndex(index);
					_recentSearchesPopup.scrollIndexToVisible(index);
				}
			}

		}
	}


}