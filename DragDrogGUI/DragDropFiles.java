import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.IOUtils;

//import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;


public class DragDropFiles extends JFrame {

    private DefaultListModel model = new DefaultListModel();
    private int count = 0;
    private JTree tree;
    private JLabel label;
    private JButton download;
    private DefaultTreeModel treeModel;
    private TreePath namesPath;
    private JPanel wrap;
    private TreePath downloadPath = null;
    private AmazonS3 s3;
    

    private static DefaultTreeModel getDefaultTreeModel(AmazonS3 s3) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("All My Buckets");
        DefaultMutableTreeNode parent;
        
        /*
        parent = new DefaultMutableTreeNode("colors");
        root.add(parent);
        
        parent.add(new DefaultMutableTreeNode("red"));
        parent.add(new DefaultMutableTreeNode("yellow"));
        parent.add(new DefaultMutableTreeNode("green"));
        parent.add(new DefaultMutableTreeNode("blue"));
        parent.add(new DefaultMutableTreeNode("purple"));
		
        parent = new DefaultMutableTreeNode("names");
        root.add(parent);
        
        parent.add(new DefaultMutableTreeNode("jack"));
        parent.add(new DefaultMutableTreeNode("kieran"));
        parent.add(new DefaultMutableTreeNode("william"));
        parent.add(new DefaultMutableTreeNode("jose"));
        
        
        parent.add(new DefaultMutableTreeNode("jennifer"));
        parent.add(new DefaultMutableTreeNode("holly"));
        parent.add(new DefaultMutableTreeNode("danielle"));
        parent.add(new DefaultMutableTreeNode("tara"));
        */
        ObjectListing objectListing = null;
        for(Bucket bucket : s3.listBuckets())
        {
        	String bname = bucket.getName();
        	parent = new DefaultMutableTreeNode(bname);
        	root.add(parent);
        	
        	objectListing = s3.listObjects(new ListObjectsRequest().withBucketName(bname));
        	for(S3ObjectSummary objectSummary : objectListing.getObjectSummaries())
        	{
        		//System.out.println(objectSummary.getKey().toString());
        		parent.add(new DefaultMutableTreeNode(objectSummary.getKey().toString()));
        		//System.out.println(objectSummary.getKey());
        	}
        }

        return new DefaultTreeModel(root);
    }

    public DragDropFiles() {
        super("Drag and Drop File Transfers in Cloud");

        //AMAZON PROFILE GRAB
        this.s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
        
        
        treeModel = getDefaultTreeModel(this.s3);
        
        tree = new JTree(treeModel);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setDropMode(DropMode.ON);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        namesPath = tree.getPathForRow(2);
        tree.expandRow(2);
        tree.expandRow(1);
        tree.setRowHeight(0);

        
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                //DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                                   //tree.getLastSelectedPathComponent();

                /* if nothing is selected */ 
                //if (node == null) return;

                /* retrieve the node that was selected */ 
                //Object nodeInfo = node.getUserObject();
                //System.out.println("Node selected is:" + nodeInfo.toString());
                /* React to the node selection. */
                downloadPath = e.getNewLeadSelectionPath();
            }
        });
        
        tree.setTransferHandler(new TransferHandler() {

            public boolean canImport(TransferHandler.TransferSupport info) {
                
                if (!info.isDrop()) {
                    return false;
                }
                info.setDropAction(COPY); 
                info.setShowDropLocation(true);
                
                if (!info.isDataFlavorSupported(DataFlavor.stringFlavor) &&
                		!info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }

                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                //************
               // System.out.println(dl.toString());
                //************
                TreePath path = dl.getPath();
                //***********
                
                
                return true;
            }

            public boolean importData(TransferHandler.TransferSupport info) {            	
            		
                if (!canImport(info)) {
                    return false;
                }
                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                
                // fetch the path and child index from the drop location
                TreePath path = dl.getPath();
                int childIndex = dl.getChildIndex();
                String bucketName = path.getLastPathComponent().toString();
                AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
                // fetch the data and bail if this fails
                String uploadName = "";
                
                Transferable t = info.getTransferable();
                try {
                    java.util.List<File> l =
                        (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);

                    for (File f : l) {
                    		uploadName = f.getName();
                    		s3.putObject(new PutObjectRequest(bucketName, uploadName, f));
                        break;//We process only one dropped file.
                    }
                } catch (UnsupportedFlavorException e) {
                    return false;
                } catch (IOException e) {
                    return false;
                }
                
                
                
                if (childIndex == -1) {
                    childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
                }

                // create a new node to represent the data and insert it into the model
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)path.getLastPathComponent();
                treeModel.insertNodeInto(newNode, parentNode, childIndex);

                // make the new node visible and scroll so that it's visible
                tree.makeVisible(path.pathByAddingChild(newNode));
                tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));
				
                //Display uploading status
                label.setText("Uploaded **" + uploadName + "** successfully!");

                return true;
            }
            
        });

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        this.wrap = new JPanel();
        this.label = new JLabel("Status Bar...");
        wrap.add(this.label);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.NORTH);

        getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
        download = new JButton("Download");
        download.addActionListener(new ActionListener() { 
        	  public void actionPerformed(ActionEvent e) { 
        	    
        	    if(downloadPath != null) {
        	    	/*
        	    		JOptionPane.showMessageDialog(null, "You like to downloand a file from cloud from buckets:" + 
        	    				downloadPath.toString());
        	    				*/
        	    	String bucketName = downloadPath.getPathComponent(1).toString();
        	    	String key = downloadPath.getLastPathComponent().toString();
        	    	S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
        	    	S3ObjectInputStream objectContent = object.getObjectContent();
        	    	boolean success = false;
        	    	try {
        	    		IOUtils.copy(objectContent, new FileOutputStream(key));
        	    		success = true;
        	    	}catch(Exception exp)
        	    	{
        	    		System.out.println("Error "+exp.getMessage());
        	    	}
        	    	
        	    	if(success)
        	    	{
        	    		label.setText("Downloaded **" + key + "** successfully!");
        	    	}
        	    	
        	    	
        	    	
        	    	
        	    }
        	  } 
        	} );

        p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        wrap = new JPanel();
        //wrap.add(new JLabel("Show drop location:"));
        wrap.add(download);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.SOUTH);

        getContentPane().setPreferredSize(new Dimension(400, 450));
    }

    private static void increaseFont(String type) {
        Font font = UIManager.getFont(type);
        font = font.deriveFont(font.getSize() + 4f);
        UIManager.put(type, font);
    }

    private static void createAndShowGUI() {
        //Create and set up the window.
        DragDropFiles test = new DragDropFiles();
        test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //Display the window.
        test.pack();
        test.setVisible(true);
    }
    
    
    private void copyFile(File source, File dest)
    		throws IOException {
	    	InputStream input = null;
	    	OutputStream output = null;
	    	try {
	    		input = new FileInputStream(source);
	    		output = new FileOutputStream(dest);
	    		byte[] buf = new byte[1024];
	    		int bytesRead;
	    		while ((bytesRead = input.read(buf)) > 0) {
	    			output.write(buf, 0, bytesRead);
	    		}
	    	} finally {
	    		input.close();
	    		output.close();
	    	}
    }

    public static void main(String[] args) {
    	
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {                
                try {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    increaseFont("Tree.font");
                    increaseFont("Label.font");
                    increaseFont("ComboBox.font");
                    increaseFont("List.font");
                } catch (Exception e) {}

                //Turn off metal's use of bold fonts
	        UIManager.put("swing.boldMetal", Boolean.FALSE);
                createAndShowGUI();
            }
        });
    }
}
