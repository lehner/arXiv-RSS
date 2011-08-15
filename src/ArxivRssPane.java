/*
 * Copyright (C) 2009 Christoph Lehner
 * 
 * All programs in this directory and subdirectories are published under the GNU
 * General Public License as described below.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Further information about the GNU GPL is available at:
 * http://www.gnu.org/copyleft/gpl.ja.html
 *
 */
package arxivrss;

import net.sf.jabref.gui.*;
import net.sf.jabref.*;
import net.sf.jabref.plugin.*;
import net.sf.jabref.imports.OAI2Fetcher;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.text.*;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;

import java.awt.event.*;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.Color;

import java.io.*;
import java.util.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLDecoder;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;


interface Console {
    public void output(String msg, boolean err);
}

class Preprint {
    public String id,title,authors,desc;
    public ArrayList<String> archives; 
    
    public Preprint() {
	id = "";
	title = "";
	authors = "";
	desc = "";
	archives = new ArrayList<String>();
    }

    public Preprint(Preprint b) {
	this.id = b.id;
	this.title = b.title;
	this.authors = b.authors;
	this.desc = b.desc;
	this.archives = new ArrayList<String>(b.archives);
    }

    @Override public boolean equals(Object b) {
	if (this==b) 
	    return true;
	if (!(b instanceof Preprint))
	    return false;
	Preprint c = (Preprint)b;
	return c.id.equalsIgnoreCase(id);
    }

    public void addArchive(String a) {
	a = a.toLowerCase();
	if (!archives.contains(a))
	    archives.add(a);
    }

    public void remArchive(String a) {
	a = a.toLowerCase();
	if (archives.contains(a))
	    archives.remove(a);
    }

    public boolean isInArchive(String a) {
	a = a.toLowerCase();
	return archives.contains(a);
    }
}

class Archive {
    private String id;
    public ArrayList<Preprint> p;

    public Archive(String id) {
	this.id = id;
	p = new ArrayList<Preprint>();
    }

    @Override public boolean equals(Object b) {
	if (this==b) 
	    return true;
	if (!(b instanceof Archive))
	    return false;
	Archive c = (Archive)b;
	return c.getId().equalsIgnoreCase(getId());
    }

    public String getId() {
	return id;
    }

    protected void addPreprint(Preprint i) {
	i.addArchive(getId());
	if (!p.contains(i)) {
	    p.add(i);
	}
    }

    public void importFrom(Archive b) {
	int i;
	for (i=0;i<b.p.size();i++) {
	    if (!p.contains(b.p.get(i))) {
		p.add(new Preprint(b.p.get(i)));
	    } else {
		p.get(p.indexOf(b.p.get(i))).addArchive(b.getId());
	    }
	}
    }
}

class RSSArchive extends Archive {

    public RSSArchive(String id) {
	super(id);
    }

    /*
     * load archive from server
     */
    public boolean load(Console p) {

	try {

	    URL url = new URL("http://export.arxiv.org/rss/" + getId() + "/");
	    HttpURLConnection con = (HttpURLConnection)url.openConnection();
	    con.setRequestProperty("User-Agent", "Jabref");
	    InputStream inputStream = con.getInputStream();
	    SAXParserFactory parserFactory = SAXParserFactory.newInstance();
	    SAXParser saxParser = parserFactory.newSAXParser();
	    saxParser.parse(inputStream, new RSSHandler(this));

	    return true;

	} catch (ParserConfigurationException e) {
	    p.output("! could not download archive " + getId() + ".\n",true);
	    p.output("! " + e.getLocalizedMessage() + "\n",true);
	} catch (SAXException e) {
	    p.output("! could not download archive " + getId() + ".\n",true);
	    p.output("! " + e.getLocalizedMessage() + "\n",true);
	} catch (MalformedURLException e) {
	    p.output("! download url '" + e.getLocalizedMessage() + "' is malformed.\n",true);
	} catch (SecurityException e) {
	    p.output("! could not download archive " + getId() + " due to security problems.\n",true);
	    p.output("! " + e.getLocalizedMessage() + "\n",true);
	} catch (IOException e) {
	    p.output("! could not download archive " + getId() + ".\n",true);
	    p.output("! " + e.getLocalizedMessage() + "\n",true);
	}
	
	return false;
    }



    class RSSHandler extends DefaultHandler {
	private Preprint it = null;
	private String parent = "", value = "";
	private RSSArchive archive;

	public RSSHandler(RSSArchive archive) {
	    this.archive = archive;
	}

	public void startDocument() throws SAXException {
	}

	public void startElement(String namespaceURI, String localName,
				 String qName, Attributes atts) throws SAXException {
	    if (qName.equalsIgnoreCase("item")) {
		it = new Preprint();
		it.id = atts.getValue("rdf:about");
		String pfx = "http://arxiv.org/abs/";
		if (it.id.startsWith(pfx)) {
		    it.id = it.id.substring(pfx.length());
		}
	    } else if (it != null) {
		parent = qName;
		value = "";
	    }
	}

	public void endElement(String namespaceURI, String localName, String qName) {
	    if (qName.equalsIgnoreCase("item")) {
		archive.addPreprint(it);
		it = null;
	    }
	    if (it != null) {
		if (parent.equalsIgnoreCase("title")) {
		    it.title = value;
		} else if (parent.equalsIgnoreCase("description")) {
		    it.desc = value;
		} else if (parent.equalsIgnoreCase("dc:creator")) {
		    it.authors = value;
		}
	    }
	}

	public void characters(char ch[], int start, int length) {
	    value += new String(ch,start,length);
	}
	
	public void ignorableWhitespace(char[] ch, int start, int length) {
	}
    }
}

class RSSArchiveManager {
    public ArrayList<RSSArchive> a;

    public RSSArchiveManager() {
	a = new ArrayList<RSSArchive>();
    }

    public boolean isAvailable(String id) {
	RSSArchive r = new RSSArchive(id);
	return a.contains(r);	
    }

    public boolean makeAvailable(String id, Console c) {
	RSSArchive r = new RSSArchive(id);
	if (!a.contains(r)) {
	    if (!r.load(c))
		return false;
	    a.add(r);
	}
	return true;
    }

    public RSSArchive get(String id) {
	RSSArchive r = new RSSArchive(id);
	if (!a.contains(r))
	    return null;
	return a.get(a.indexOf(r));
    }
}

class RSSBrowseDialog extends JPanel
    implements ActionListener, WindowListener, ItemListener, HyperlinkListener, Console {

    private JFrame d;
    private JEditorPane htmlView;
    public JPanel pSelect;
    private JComboBox pSearch;
    private ReadArchives rTask;
    private String err = "";
    private String previousSearch = "";
    private RSSArchiveManager allArchives = new RSSArchiveManager();
    private Archive selArchive = new Archive("");
    private boolean runInit = false;
    private UpdateHtmlViewThread uTask = null;
    private JabRefFrame frame;
    private int maxSearchCount = 15;

    class ReadArchives extends Thread {
	private RSSBrowseDialog p;

	public ReadArchives(RSSBrowseDialog p) {
	    this.p = p;
	}

        public void run() {
	    p.addArchives(p.pSelect);
	}
    }

    class UpdateHtmlViewThread extends Thread {
	private RSSBrowseDialog p;
	private boolean shouldRestart = false;
	
	public UpdateHtmlViewThread(RSSBrowseDialog p) {
	    this.p = p;
	}

	public void restart() {
	    shouldRestart = true;
	}

	void readArchive(String archive) {
	}

        public void run() {
	    p.pSearch.setEnabled(false);
	    do {
		String status = "Loading...";
		
		shouldRestart = false;
		ArrayList<String> al = new ArrayList<String>(Arrays.asList(Globals.prefs.getStringArray("arxiv-rss-selection")));
		String[] ar = new String[al.size()];
		al.toArray(ar);
		
		boolean err = false;
		selArchive = new Archive("");
		for (int i=0;i<ar.length && !shouldRestart;i++) {
		    if (!allArchives.isAvailable(ar[i])) {
			status += "<br>* " + ar[i];
			htmlView.setText(status);
		    }
		    if (allArchives.makeAvailable(ar[i],p)) {
			selArchive.importFrom(allArchives.get(ar[i]));
		    } else {
			err = true;
		    }
		}
		
		if (!err)
		    p.formatContent();
	    } while (shouldRestart);
	    p.pSearch.setEnabled(true);
	}	
    }

    private void updateHtmlView() {
	if (uTask != null && uTask.isAlive()) {
	    uTask.restart();
	} else {
	    uTask = new UpdateHtmlViewThread(this);
	    uTask.start();
	}
    }

    private void setArchive(String archive,boolean use) {
	ArrayList<String> al = new ArrayList<String>(Arrays.asList(Globals.prefs.getStringArray("arxiv-rss-selection")));
	if (al.contains(archive) && !use) {
	    al.remove(archive);
	} else if (!al.contains(archive) && use) {
	    al.add(archive);
	}
	String[] ar = new String[al.size()];
	Globals.prefs.putStringArray("arxiv-rss-selection",al.toArray(ar));

	if (!runInit)
	    updateHtmlView();
    }

    private void addArchive(JPanel p, String a) {
	List<String> al = Arrays.asList(Globals.prefs.getStringArray("arxiv-rss-selection"));
	JCheckBox c = new JCheckBox(a);
	c.setBackground(Color.white);
	c.addItemListener(this);
	p.add(c);
	c.setSelected(al.contains(a));
    }

    public void output(String msg, boolean berr) {
	if (!berr)
	    System.out.println(msg);
	else {
	    err += msg + "<br>";
	    htmlView.setText("<font color=\"red\">" + err + "</font>");
	}
    }

    public void addArchives(JPanel p) {
	JLabel waitLabel = new JLabel("fetching archives ...",new ImageIcon(GUIGlobals.getIconUrl("openUrl")),JLabel.CENTER);
	p.add(waitLabel);
	runInit = true;

	try {
	    URL url = new URL("http://export.arxiv.org/rss/");
	    URLConnection con = url.openConnection();
	    con.setRequestProperty("User-Agent", "Jabref");
	    InputStream input = new BufferedInputStream(con.getInputStream());
	    BufferedReader html = new BufferedReader(new InputStreamReader(input));

	    String line, fullHTML = "";
	    while( (line = html.readLine()) != null ) {
		fullHTML += line;
	    }
	    
	    Pattern pa = Pattern.compile("are (.*?)\\.",Pattern.CASE_INSENSITIVE);
	    Matcher m = pa.matcher(fullHTML);
	    if (m.find()) {
		String[] al = m.group(1).split(",");
		for (int i=0;i<al.length;i++) {
		    addArchive(p,al[i].trim());
		}
	    }

	    p.remove(waitLabel);

	    revalidate();
	    repaint();

	    updateHtmlView();

	} catch (MalformedURLException e) {
	    output("! download url '" + e.getLocalizedMessage() + "' is malformed.\n",true);
	} catch (SecurityException e) {
	    output("! could not download archives list due to security problems.\n",true);
	    output("! " + e.getLocalizedMessage() + "\n",true);
	} catch (Exception e) {
	    output("! could not download archives list.\n",true);
	    output("! " + e.getLocalizedMessage() + "\n",true);
	}

	runInit = false;
    }

    public void formatContent() {
	formatContent(previousSearch);
    }

    public boolean formatMatches(String s, String[] fa) {
	s = s.toLowerCase();
	int i;
	for (i=0;i<fa.length;i++) {
	    if (!s.contains(fa[i]))
		return false;
	}
	return true;
    }

    public String formatHL(String s, String[] fa) {
	int i,j,pos;
	if (fa.length == 1 && fa[0].length() == 0)
	    return s;

	String ps = "(?i)(";
	for (i=0;i<fa.length;i++) {
	    if (i > 0)
		ps += "|";
	    ps += Pattern.quote(fa[i]);
	}
	ps += ")";

	return s.replaceAll(ps,"<span style=\"background:yellow;\">$1</span>");
    }

    public String formatAuthors(String a) {
	return a.replaceAll("(?i)<a href=.*?>(.*?)</a>","$1");
    }

    public BibtexEntry findEprint(String id) {
	BasePanel panel = frame.basePanel();
	if (panel != null) {

	    BibtexDatabase db = panel.database();
	    if (db != null) {

		Iterator<String> ki = db.getKeySet().iterator();
		while (ki.hasNext()) {
		    BibtexEntry be = db.getEntryById(ki.next());
		    String eprint = be.getField("eprint");
		    if ((eprint != null) && (eprint.equals(id)))
			return be;
		}			
	    }
	}

	return null;
    }

    public void formatContent(String sel) {
	String html = ""; // <style>td{font-family:Monospace;}</style>
	Pattern pd;

	sel = sel.trim().toLowerCase();
	sel = sel.replaceAll(" [ ]+"," ");
	String[] fa = sel.split(" ");

	int i;

	for (i=0;i<selArchive.p.size();i++) {
	    Preprint it = selArchive.p.get(i);
	    String aut = formatAuthors(it.authors);
	    if (formatMatches(it.id + " " + aut + " " + it.desc + " " + it.title,fa)) {
		
		String modT = "";
		if (findEprint(it.id) != null) {
		    modT = "bgcolor = #ffff88";
		}

		html+="<table " + modT + "><tr valign=\"top\"><td><font color=\"blue\">" + formatHL(aut,fa) + "</font></td>" + 
		    "<td width=\"31%\" align=\"right\"><a href=\"prp:" + it.id + "\">Pdf</a> | <a href=\"pra:" + 
		    it.id + "\">Html</a> | " + "<a href=\"imp:" + it.id + "\"><nobr>Import " + formatHL(it.id,fa) + "</nobr></a>" +
		    "</td></tr><tr><td colspan=\"2\"><b>" + formatHL(it.title,fa) + "</b></td>" + 
		    "</tr><tr><td colspan=\"2\">" + formatHL(it.desc,fa) + "</td></tr></table><br>";
	    }
	
	}
	
	if (html.length() == 0) {
	    html = "No articles found.";
	}
	
	htmlView.setText(html);
	previousSearch = sel;
    }

    private void performSearch(String s) {
	formatContent(s);
    }

    private void addSearchItem(String s) {
	s = s.trim();
	if (s.length() > 0) {
	    int i,n;

	    n = pSearch.getItemCount();
	    for (i=0;i<n;i++) {
		if (pSearch.getItemAt(i).equals(s)) {
		    pSearch.removeItemAt(i);
		    break;
		}
	    }
	    pSearch.insertItemAt(s,0);

	    while (pSearch.getItemCount()>maxSearchCount)
		pSearch.removeItemAt(maxSearchCount);

	    n = pSearch.getItemCount();
	    String[] ar = new String[n];
	    for (i=0;i<n;i++) {
		ar[i] = pSearch.getItemAt(i).toString();
	    }
	    Globals.prefs.putStringArray("arxiv-rss-search",ar);
	}
    }

    private void addSearchItems() {
	ArrayList<String> al = new ArrayList<String>(Arrays.asList(Globals.prefs.getStringArray("arxiv-rss-search")));
	for (int i=0;i<al.size();i++) {
	    pSearch.insertItemAt(al.get(i),i);
	}
    }

    public RSSBrowseDialog(JabRefFrame frame, JFrame d) {
        super(new BorderLayout());

	this.frame = frame;
	this.d = d;

	pSelect = new JPanel();
	JPanel pSelectBorder = new JPanel();
	JScrollPane pScrollSelect = new JScrollPane(pSelectBorder);
	pSelect.setLayout(new BoxLayout(pSelect, BoxLayout.Y_AXIS));

	pSelect.setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
	pSelect.setBackground(Color.white);
	pSelectBorder.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
	pSelectBorder.setBackground(Color.white);
	pSelectBorder.add(pSelect);

	htmlView = new JEditorPane("text/html","Please stand by while data from the arXiv is obtained.");

	JScrollPane pScrollBrowse = new JScrollPane(htmlView);
	JPanel pBrowse = new JPanel(new BorderLayout());
	JPanel pSearchPanel = new JPanel(new BorderLayout());

	htmlView.setEditable(false);
	htmlView.setBackground(Color.white);
	htmlView.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
	htmlView.addHyperlinkListener(this);

	pScrollBrowse.setBackground(Color.white);

	pBrowse.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));
	pBrowse.add(pScrollBrowse, BorderLayout.CENTER);

	pSearch = new JComboBox();
	pSearch.addActionListener(this);
	pSearch.setEditable(true);
	addSearchItems();

	pSearchPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
	pSearchPanel.add(pSearch, BorderLayout.CENTER);

	pBrowse.add(pSearchPanel, BorderLayout.PAGE_END);

        add(pScrollSelect, BorderLayout.LINE_START);
        add(pBrowse, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(15,15,15,15));

	rTask = new ReadArchives(this);
	rTask.start();
    }


    public void importArxiv(String id) {
	BasePanel panel = frame.basePanel();
	if (panel != null) {
	    OAI2Fetcher f = new OAI2Fetcher();
	    BibtexEntry be = f.importOai2Entry(id);
	    if (be == null) {
		JOptionPane.showMessageDialog(this,
					      "Could not import " + id,
					      "OAI2Fetcher error",
					      JOptionPane.ERROR_MESSAGE);
	    } else {
		BibtexDatabase db = panel.database();
		try {
		    if (be.getCiteKey() == null || be.getCiteKey().length() == 0) {
			be.setField(BibtexFields.KEY_FIELD,"arxiv-" + id);
		    }
		    db.insertEntry(be);
		    panel.markBaseChanged();
		    JOptionPane.showMessageDialog(this,
						  "Import of " + id + " successful.",
						  "Status",JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
		    JOptionPane.showMessageDialog(this,
						  ex.getMessage(),
						  "Import error",
						  JOptionPane.ERROR_MESSAGE);
		}
	    }
	} else {
	    JOptionPane.showMessageDialog(this,
					  "There is no active database",
					  "Import error",
					  JOptionPane.ERROR_MESSAGE);
	}
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
	if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
	    if (e.getDescription().startsWith("imp:")) {
		String id = e.getDescription().substring(4);
		importArxiv(id);
	    } else if (e.getDescription().startsWith("prp:")) {
		String id = e.getDescription().substring(4);
		try {
		    Util.openExternalViewer(null,"http://arxiv.org/pdf/" + id,"url");
		} catch (IOException x) {
		    System.err.println("Could not open external viewer: " + x.getMessage());
		}
	    } else if (e.getDescription().startsWith("pra:")) {
		String id = e.getDescription().substring(4);
		try {
		    Util.openExternalViewer(null,"http://arxiv.org/abs/" + id,"url");
		} catch (IOException x) {
		    System.err.println("Could not open external viewer: " + x.getMessage());
		}
	    }
	}
    }
    
    public void actionPerformed(ActionEvent evt) {
	if (evt.getSource() == pSearch) {
	    String find = (String)pSearch.getSelectedItem();
	    if (evt.getActionCommand().equalsIgnoreCase("comboBoxChanged")) {
		performSearch(find);
	    } else if (evt.getActionCommand().equalsIgnoreCase("comboBoxEdited")) {
		addSearchItem(find);
	    }
	}
    }

    public void itemStateChanged(ItemEvent e) {
	JCheckBox cb = (JCheckBox)e.getSource();
	setArchive(cb.getText(),cb.isSelected());
    }

    public void windowClosing(WindowEvent e) {
	d.dispose();
    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowOpened(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }

    static void createAndShow(JabRefFrame frame) {
        JFrame d = new JFrame("Browse new preprints on arXiv");
        RSSBrowseDialog p = new RSSBrowseDialog(frame,d);
        p.setOpaque(true);
        d.setContentPane(p);
	d.addWindowListener(p);
	d.setPreferredSize(new Dimension(1000,600));
	d.setMinimumSize(new Dimension(500,300));
	d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        d.pack();
	d.setLocationRelativeTo(null);
	d.setVisible(true);
    }
}


class ArxivRssSidePaneComponent extends SidePaneComponent {

    public ArxivRssSidePaneComponent(SidePaneManager manager) {
	super(manager, GUIGlobals.getIconUrl("openUrl"), "");
    }

    public void setActiveBasePanel(BasePanel panel) {
        super.setActiveBasePanel(panel);
    }

}


public class ArxivRssPane implements SidePanePlugin, ActionListener {

    protected SidePaneManager manager;
    private JMenuItem showMenu;
    private JabRefFrame frame;

    public void init(JabRefFrame frame, SidePaneManager manager) {
	this.manager = manager;
	this.frame = frame;

	showMenu = new JMenuItem("Browse new preprints (arXiv)",new ImageIcon(GUIGlobals.getIconUrl("search")));
	showMenu.setMnemonic(KeyEvent.VK_A);
	showMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, ActionEvent.ALT_MASK|ActionEvent.CTRL_MASK));

	showMenu.addActionListener(this);

	Globals.prefs.putDefaultValue("arxiv-rss-selection","");
	Globals.prefs.putDefaultValue("arxiv-rss-search","");
    }
    
    public SidePaneComponent getSidePaneComponent() {
	return new ArxivRssSidePaneComponent(manager);
    }

    public JMenuItem getMenuItem() {
	return showMenu;
    }

    public String getShortcutKey() {
	return "ctrl alt B";
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == showMenu) {
	    RSSBrowseDialog.createAndShow(frame);
	}
    }
}
