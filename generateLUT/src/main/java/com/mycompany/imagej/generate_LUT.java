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
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class generate_LUT implements PlugIn {
	public ImagePlus imp;
	public boolean canContinue,didCancel;
	public double[] filmavgs = new double[4],refavgs = new double[4];
	public JFrame roiguide;
	public JLabel refLabel,filmLabel;
	
	public void run(String arg){
		imp = IJ.getImage();
        if (imp==null){
        	IJ.noImage(); // get and check for image, return if there is none
        	return;
        }
        if(!(imp.getNChannels()==4)||!(imp.getNFrames()==1)) IJ.error("Use 4 channel, single frame image!");

        getRef();
        // make LUT after getting values
        IJ.log("refChannelN,filmChannelN");
		for(int i=1;i<5;i++){
			IJ.log(""+refavgs[i-1]+","+filmavgs[i-1]);
		}
	}	
	
	public void getRef(){
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(2,1,0,0));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);
		
		JButton referenceButton = new JButton("Get reference intensity");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					refavgs[i-1]=takeroimean(imp.getRoi());
				}
			}
		});
		selectPanel.add(referenceButton);
		
		JButton filmButton = new JButton("Get film intensities");
		filmButton.setEnabled(true);
		filmButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					filmavgs[i-1]=takeroimean(imp.getRoi());
				}
		        IJ.log((""+(imp.getRoi()==null)));
			}
		});
		

		
		filmLabel = new JLabel("");
		selectPanel.add(filmButton);
		selectPanel.add(filmLabel);
		
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
			}
		});
		buttonPanel.add(cancelButton);
		
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
					canContinue=true;
					roiguide.dispose();	
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		roiguide = new JFrame("Get film and reference reflectance values:");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(400,400);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();
		
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
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
