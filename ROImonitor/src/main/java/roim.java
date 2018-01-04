import ij.*;
import ij.plugin.PlugIn;
import ij.plugin.frame.*;
import ij.process.*;
import ij.gui.*;
import ij.gui.Plot;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.*;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.apache.commons.lang.time.StopWatch;
import ij.measure.ResultsTable;
/*
 * This plugin takes regions of interests and monitors the mean intensity of each of them
 */
public class roim
        implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener, Runnable {
    private ImagePlus imp;                  //the ImagePlus that we listen to and the last one
    private ImagePlus plotImage;            //where we plot the profile
    private Thread bgThread;                //thread for plotting
    private boolean doUpdate,didCancel,canContinue;
    public double countval=1,max=0,min=0,reference,background,spot,background1,spot1,background2,spot2,normspot,
    		normspot1,normspot2,spotno,roino;
    public int ref,current=0,sno=0,rno=0,bno=0;
    public ArrayList<Double> f = new ArrayList<Double>();
    public ArrayList<ArrayList<Double>> data = new ArrayList<ArrayList<Double>>();
    public ResultsTable table  = new ResultsTable();
    public ResultsTable resultsTable;
    public RoiManager rm;
    public Overlay overlay = new Overlay();
    public Plot plot;
    public StopWatch timer;
    public JFrame roiguide,check;
    public JLabel refLabel,spotLabel,backgroundLabel;
    public JPanel selectPanel,buttonPanel;
    public JButton referenceButton,backgroundButton,spotButton;
    public DecimalFormat printformat = new DecimalFormat("0");
    
    /* Initialization and plot for the first time. Later on, updates are triggered by the listeners **/
    public void run(String arg) {
    	
        imp = WindowManager.getCurrentImage();
        if (imp==null){
        	IJ.noImage(); // get and check for image, return if there is none
        	return;
       	}
    	
    	GenericDialog gd = new GenericDialog("Region of Interest Monitor");
    	gd.addNumericField("Choose number of spots: ", 1, 1);
    	gd.showDialog();
		if (gd.wasCanceled())
			return;
    	spotno=gd.getNextNumber();
    	roino=(3+((spotno-1)*2));
    	
    	if(!checkoverlay(imp)){
    		overlay.clear();
    		Color blue = new Color(0,0,255);
	    	overlay.setLabelColor(blue);
	    	overlay.drawLabels(true);
	    	overlay.drawNames(true);
	    	imp.setOverlay(overlay);
	    	if(!roiSelector(imp)) 
	    		return;
    	}else {
    		current=(int) roino;
    	}
    	
    	timer = new StopWatch();
    	timer.start();
    	
        if (current!=roino) { // make sure we have image and ROIs before continuing
            IJ.error("ROI Monitor","Please Select "+printformat.format(roino)+" ROIs");
            return;
        }
        
        ImageProcessor ip = getPlot();  // get image processor of plot
        
        if (ip==null) {                     // check if successful
            IJ.error("ROI Monitor","Data acquisition failed."); return;
        }
                                            // new plot window
        plotImage = new ImagePlus("Intensity of "+imp.getShortTitle(), ip);
        plotImage.show();
        IJ.wait(50);
        positionPlotWindow();
                                            // thread for plotting in the background
        bgThread = new Thread(this, "Intensity Monitoring");
        bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
        bgThread.start();
        createListeners();
    }
    
    public boolean checkoverlay(ImagePlus imp) {
    	Overlay test = imp.getOverlay();
    	int a;
    	if(test!=null) {
    		overlay=test;
    		a = test.size();
    		return (a==roino);
    	}else {
    		return (false); // doesnt check names for now, will just look at number of rois
    	}
    	}

    public boolean roiSelector(ImagePlus imp) {	
    	//GridBagConstraints constraint = new GridBagConstraints(); maybe use to make GUI neat
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(3,2,0,0));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);
		
		referenceButton = new JButton("Add reference region");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				refDefine();
			}
		});
		refLabel = new JLabel(" ("+printformat.format(rno)+"/1)");
		selectPanel.add(referenceButton);
		selectPanel.add(refLabel);
		
		backgroundButton = new JButton("Add background 1");
		backgroundButton.setEnabled(true);
		backgroundButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				brDefine();
			}
		});
		backgroundLabel = new JLabel(" ("+printformat.format(bno)+"/"+printformat.format(spotno)+")");
		selectPanel.add(backgroundButton);
		selectPanel.add(backgroundLabel);
		
		spotButton = new JButton("Add spot 1");
		spotButton.setEnabled(true);
		spotButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				spotDefine();
			}
		});
		spotLabel = new JLabel(" ("+printformat.format(sno)+"/"+printformat.format(spotno)+")");
		selectPanel.add(spotButton);
		selectPanel.add(spotLabel);
	    
		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide.dispose();
				overlay.clear();
			}
		});
		buttonPanel.add(cancelButton);
		
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if(current!=roino){
					IJ.error("ROI Manager","Please select " + printformat.format(roino) + " regions of interest.");
				}else{
					canContinue=true;
					roiguide.dispose();	
				}
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		roiguide = new JFrame("Add regions of interest:");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(400,400);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();

		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return !didCancel; // if user pressed cancel return false
	}
    
    public void refDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select the reference region."); return;
    	}else {
    		rno++;
        	current++;
        	Roi roi=imp.getRoi();
        	roi.setName("Reference");
    		overlay.addElement(roi);
    		refLabel.setText("("+printformat.format(rno)+"/1)");
    	}
		if(rno==1){
			referenceButton.setEnabled(false);
		}
    	imp.killRoi();
    }
    
    public void brDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a background region"); return;
    	}else {
    		bno++;
        	current++;
        	Roi roi=imp.getRoi();
        	roi.setName("Background "+bno);
    		overlay.addElement(roi);
    		backgroundButton.setText("Add background "+printformat.format(bno+1));
    		backgroundLabel.setText("("+printformat.format(bno)+"/"+printformat.format(spotno)+")");
    	}
		if(bno==spotno){
			backgroundButton.setEnabled(false);
		}
    	imp.killRoi();
    	}
    
    public void spotDefine(){
    	if(imp.getRoi()==null){
            IJ.error("ROI Monitor","Please select a spot."); return;
    	}else {
    		if((sno+1)>spotno){
    			IJ.error("ROI Monitor","Select only " + printformat.format(spotno) + " spot(s).");
    		}else{
    			sno++;
        		current++;
        		Roi roi=imp.getRoi();
            	roi.setName("Spot "+sno);
        		overlay.addElement(roi);
    			spotButton.setText("Add spot "+printformat.format(sno+1));
    			spotLabel.setText("("+printformat.format(sno)+"/"+printformat.format(spotno)+")");
    		}
    		if(sno==spotno){
    			spotButton.setEnabled(false);
    		}
    	}
    	imp.killRoi();
    }
    

	
	// these listeners are activated if the selection is changed in the corresponding ImagePlus
    public synchronized void mousePressed(MouseEvent e) { doUpdate = true; notify(); }   
    public synchronized void mouseDragged(MouseEvent e) { doUpdate = true; notify(); }
    public synchronized void mouseClicked(MouseEvent e) { doUpdate = true; notify(); }
    public synchronized void keyPressed(KeyEvent e) { doUpdate = true; notify(); }
    // unused listeners concerning actions in the corresponding ImagePlus
    public void mouseReleased(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseMoved(MouseEvent e) {}
    public void keyTyped(KeyEvent e) {}
    public void keyReleased(KeyEvent e) {}
    public void imageOpened(ImagePlus imp) {}

    // this listener is activated if the image content is changed (by imp.updateAndDraw)
    public synchronized void imageUpdated(ImagePlus imp) {
        if (imp == this.imp) { 
            doUpdate = true;
            notify();
        }
    }

    // if either the plot image or the image we are listening to is closed, exit
    public void imageClosed(ImagePlus imp) {
        if (imp == this.imp || imp == plotImage) {
            removeListeners();
            closePlotImage();                       //also terminates the background thread
        }
    }

    // the background thread for plotting.
    public void run() {
        while (true) {
            IJ.wait(40);                            //delay to make sure the roi has been updated
            ImageProcessor ip = getPlot();
            if (ip != null) plotImage.setProcessor(null, ip);
            synchronized(this) {
                if (doUpdate) {
                    doUpdate = false;               //and loop again
                } else {
                    try {wait();}                   //notify wakes up the thread
                    catch(InterruptedException e) { //interrupted tells the thread to exit
                        return;
                    }
                }
            }
        }
    }

    private synchronized void closePlotImage() {    //close the plot window and terminate the background thread
        bgThread.interrupt();
        plotImage.getWindow().close();
    }

    private void createListeners() {
        ImageWindow win = imp.getWindow();
        ImageCanvas canvas = win.getCanvas();
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);
        imp.addImageListener(this);
        plotImage.addImageListener(this);
    }

    private void removeListeners() {
        ImageWindow win = imp.getWindow();
        ImageCanvas canvas = win.getCanvas();
        canvas.removeMouseListener(this);
        canvas.removeMouseMotionListener(this);
        canvas.removeKeyListener(this);
        imp.removeImageListener(this);
        plotImage.removeImageListener(this);
    }

    /** Place the plot window to the right of the image window */
    void positionPlotWindow() {
        IJ.wait(500);
        if (plotImage==null || imp==null) return;
        ImageWindow pwin = plotImage.getWindow();
        ImageWindow iwin = imp.getWindow();
        if (pwin==null || iwin==null) return;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension plotSize = pwin.getSize();
        Dimension imageSize = iwin.getSize();
        if (plotSize.width==0 || imageSize.width==0) return;
        Point imageLoc = iwin.getLocation();
        int x = imageLoc.x+imageSize.width+10;
        if (x+plotSize.width>screen.width)
            x = screen.width-plotSize.width;
        pwin.setLocation(x, imageLoc.y);
        ImageCanvas canvas = iwin.getCanvas();
        canvas.requestFocus();
    }

    //update plot and give imageprocessor
	ImageProcessor getPlot() {
        makedata();
        String xLabel = "Time (s)";
        String yLabel = "Normalized Intensity";
        Color[] colors = {Color.blue,Color.green,Color.red,Color.cyan,Color.yellow};
    	plot = new Plot("",xLabel,yLabel);
        for(int i=0;i<5 && i<spotno;i++){
        	plot.setLimits(0, f.get(f.size()-1), min, max);
        	plot.setColor(colors[i]);
        	plot.addPoints(f, data.get((i*3)+3), PlotWindow.LINE);
        	plot.addLabel(0.025, (0.1*i)+0.1, "Spot "+(i+1));
        }
        return plot.getProcessor();
    }

    public synchronized void makedata(){
        for(int i=0;i<(spotno*3)+1;i++) {
        	data.add(new ArrayList<Double>());
        }
        double tval=(double) timer.getTime();
        f.add(tval/1000);
        table.show("Time || Intensity");
		table.incrementCounter();
        reference=takeroimean(overlay.get(overlay.getIndex("Reference")));
        table.addValue("Reference Intensity",reference);
        data.get(0).add(reference);
        for(int i=0;i<spotno;i++) {
        	background=takeroimean(overlay.get(overlay.getIndex("Background "+(i+1))));
        	spot=takeroimean(overlay.get(overlay.getIndex("Spot "+(i+1))));
        	normspot=(spot-background)/reference;
            if(normspot>max)max=(normspot*1.1);
            if(normspot<min&&normspot>0)min=(normspot*.9);
            if(normspot<min&&normspot<0)min=(normspot*1.1);
            data.get((i*3)+1).add(background);
            data.get((i*3)+2).add(spot);
            data.get((i*3)+3).add(normspot);
    		table.addValue("Time",tval/1000);
    		table.addValue("Normalized Spot-"+(i+1)+" Intensity",normspot);
    		table.addValue("Spot-"+(i+1)+" Intensity",spot);
    		table.addValue("Background-"+(i+1)+" Intensity",background);
        }
        IJ.wait(70);
    }

	public double takeroimean(Roi roi) {
		ImagePlus imp = IJ.getImage();
		if (roi!=null && !roi.isArea()) roi = null;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi!=null?roi.getMask():null;
		Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
		double sum = 0;
		int count = 0;
		for(int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask==null||mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixelValue(x+r.x, y+r.y);
				}
			}
		}
		return (sum/count);
		}
}
    

