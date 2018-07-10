/*
 * apply_LUT by Michael Wallace
 * 
 * Template from IJ Process Pixels Plugin
 */

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ij.IJ;
import ij.io.*;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.ResultsTable;
import ij.plugin.*;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

public class apply_LUT implements PlugIn {
	public ImagePlus imp;
	public ResultsTable lut;
	public int ind;
    public boolean oobret,canContinue=false,didCancel=false;
    public double refval;
    public JButton referenceButton;
	
	public void run(String arg) {
		// get and check for image, return if there is none, otherwise, take stack size
		imp = WindowManager.getCurrentImage();
        if (imp==null){
        	IJ.noImage(); 
        	return;
        }
        getRef();
        
        IJ.run(imp, "32-bit", "");
        IJ.run(imp, "Divide...", "value="+refval);
        
		int stacks = imp.getStackSize();
		
		//ask for LUT path
        OpenDialog LUT = new OpenDialog("Load a LUT File.");
        while(LUT.getPath()==null){
        	IJ.wait(200);
        }
        
        //Load LUT into text window
    	String path = LUT.getPath();
        TextWindow tw = new TextWindow(path,100,300);
        TextPanel tp = tw.getTextPanel();
        
        // Read LUT values
        int lutlen = tp.getLineCount();
        float[][] lut = new float[lutlen][2];
        for(int i=1;i<=tp.getLineCount()-1;i++){
        	String s[] = tp.getLine(i).split("    ");
        	lut[i][1]=Float.parseFloat(s[0]);
        	lut[i][0]=Float.parseFloat(s[1]);
        }
        
        // Close window
        tw.dispose(); 
        
        //interpolate from normalized intensity to height based on the loaded lookup table for all pixels/frames
    	int lw[]=imp.getDimensions();
        float pixels[][] = null;
        for(int i=1;i<=stacks;i++){
        	IJ.showProgress(i, stacks);
        	IJ.showStatus("Applying LUT: "+"("+i+"/"+stacks+")");
        	imp.setSlice(i);
        	pixels=imp.getProcessor().getFloatArray();
        	for(int j=0;j<lw[0];j++)
        		for(int k=0;k<lw[1];k++){
        			pixels[j][k]=interpolate(lut,pixels[j][k],lutlen);
        		}
            imp.getProcessor().setFloatArray(pixels);
        }
        
        // update and draw new height map
        imp.updateAndDraw();
	}
	
	private boolean getRef() {
    	//GridBagConstraints constraint = new GridBagConstraints(); maybe use to make GUI neat
    	JPanel selectPanel = new JPanel();
    	selectPanel.setLayout(new GridLayout(3,2,0,0));
    	selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    	imp.getWindow().toFront();
    	IJ.setTool(Toolbar.RECTANGLE);
    	//initialize buttons used for adding regions of interest
    	JButton referenceButton = new JButton("Get reference intensity");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
					refval=takeroimean(imp.getRoi());
					imp.killRoi();
				}
		});
		selectPanel.add(referenceButton);
		final JFrame roiguide1 = new JFrame("Select and record reference region intensity:");
		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide1.dispose();
			}
		});
		buttonPanel.add(cancelButton);
		//add OK button and behavior
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				canContinue=true;
				roiguide1.dispose();	
			}
		});
		buttonPanel.add(okButton);

    	
		// Create and populate the JFrame
		roiguide1.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide1.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide1.setLocation(200,300);
		roiguide1.setVisible(true);
		roiguide1.setResizable(false);
		roiguide1.pack();

		// Wait for user to click either Cancel or OK button
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
    
	public double takeroimean(Roi roi) {
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

	// basic interpolation function--returns 0 in the event there are no points that given value is between
	public float interpolate(float data[][],float input, int size){
		int ind=-1;
		for(int i=0;i<size-1;i++){
			if(input>data[i][0] && input<data[i+1][0])
				ind=i;
		}

		if(ind!=-1){
			float x1 = data[ind][0];
			float x2 = data[ind+1][0];
			float y1 = data[ind][1];
			float y2 = data[ind+1][1];
			return ((((y2-y1)/(x2-x1))*(input-x1))+y1);
		}else {
			return 0;
		}
	}
}