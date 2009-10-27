package org.docx4j.convert.out.pdf.viaXSLFO;

import java.util.*;

import org.docx4j.UnitsOfMeasurement;
import org.docx4j.XmlUtils;
import org.docx4j.convert.out.ModelConverter;
import org.docx4j.convert.out.html.HtmlExporterNG;
import org.docx4j.model.Model;
import org.docx4j.model.properties.Property;
import org.docx4j.model.properties.PropertyFactory;
import org.docx4j.model.properties.table.BorderBottom;
import org.docx4j.model.properties.table.BorderLeft;
import org.docx4j.model.properties.table.BorderRight;
import org.docx4j.model.properties.table.BorderTop;
import org.docx4j.model.styles.StyleTree;
import org.docx4j.model.table.Cell;
import org.docx4j.model.table.TableModel;
import org.docx4j.wml.Style;
import org.docx4j.wml.TblBorders;
import org.docx4j.wml.TblGridCol;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.log4j.Logger;
import javax.xml.transform.TransformerException;

/*
 *  @author Adam Schmideg, Jason Harrop
 *  
*/
public class TableWriter extends ModelConverter {
	
  private final static Logger logger = Logger.getLogger(TableWriter.class);
  
  public final static String TABLE_BORDER_MODEL = "border-collapse";

  public Node toNode(Model tableModel) throws TransformerException {
    TableModel table = (TableModel)tableModel;
    logger.debug("Table asXML:\n" + table.debugStr());
    
    org.w3c.dom.Document doc = XmlUtils.neww3cDomDocument();   
	DocumentFragment docfrag = doc.createDocumentFragment();

    Element foTable = doc.createElementNS("http://www.w3.org/1999/XSL/Format", "fo:table");
    docfrag.appendChild(foTable);
    
	// Write effective table styles
	List<Property> properties = PropertyFactory.createProperties(table.getEffectiveTableStyle().getTblPr());
	// This handles:
	// - position (tblPr/tblInd)
	// - table-layout
	for( Property p :  properties ) {
		if (p!=null) {
			p.setXslFO(foTable);
		}
	}
	// Borders, shading
	if (table.getEffectiveTableStyle().getTcPr()!=null) {
		properties = PropertyFactory.createProperties(table.getEffectiveTableStyle().getTcPr());
		for( Property p :  properties ) {
			if (p!=null) {
				p.setXslFO(foTable);
			}
		}
	}
    // TODO 
//	table.getEffectiveTableStyle().getPPr();
//	table.getEffectiveTableStyle().getTblStylePr();
    
	TblBorders tblBorders = null;
	if (table.getEffectiveTableStyle().getTblPr()!=null) {
		tblBorders = table.getEffectiveTableStyle().getTblPr().getTblBorders();
		// will apply these as a default on each td, and then override
	}	
	
//	if (logger.isDebugEnabled()) {					
//		logger.debug(XmlUtils.marshaltoString(tbl, true, true));					
//	}

	// vAlign fix: match Word's default of top
	if (table.getEffectiveTableStyle().getTcPr()==null
			|| table.getEffectiveTableStyle().getTcPr().getVAlign()==null) {
		foTable.setAttribute(org.docx4j.model.properties.table.tc.TextAlignmentVertical.FO_NAME, 
				"top");
	}

	// border model
	if (table.isBorderConflictResolutionRequired() ) {
		foTable.setAttribute(TABLE_BORDER_MODEL, "collapse");		
	} else {
		foTable.setAttribute(TABLE_BORDER_MODEL, "separate"); // this is the default in CSS				
	}
	
	// column widths
    int cols = table.getColCount();
    int tWidth = 0;
    if (table.getTblGrid()!=null) {
    	int i = 0;
    	for( TblGridCol gridCol : table.getTblGrid().getGridCol() ) {   
	        String s = String.valueOf(i);
	        Element foTableColumn = doc.createElementNS("http://www.w3.org/1999/XSL/Format", 
	        		"fo:table-column");
	        foTable.appendChild(foTableColumn);
	        foTableColumn.setAttribute("column-number", s);

	        int width = gridCol.getW().intValue();	 
	        tWidth += width;
	        
	        foTableColumn.setAttribute("width", UnitsOfMeasurement.twipToBest(width) );
	        //foTableColumn.setAttribute("colname", table.getColName(i));
	        i++;
    	}
		foTable.setAttribute("width", UnitsOfMeasurement.twipToBest(tWidth) );		
    	
      } else {
    	    for (int i = 0; i < cols; i++) {
    	        String s = String.valueOf(i);
    	        Element foTableColumn = doc.createElementNS("http://www.w3.org/1999/XSL/Format", 
    	        		"fo:table-column");
    	        foTable.appendChild(foTableColumn);
    	        foTableColumn.setAttribute("column-number", s);
    	    }    	  
      }
	    
	Node foTableBody = doc.createElementNS("http://www.w3.org/1999/XSL/Format", 
	"fo:table-body");			
	foTable.appendChild(foTableBody);
    
    for (List<Cell> rows : table.getCells()) {
			// Element row = doc.createElement("tr");
			Element row = doc.createElementNS("http://www.w3.org/1999/XSL/Format", 
			"fo:table-row");			
			foTableBody.appendChild(row);
			for (Cell cell : rows) {
				// process cell
				if (!cell.isDummy()) {
					int col = cell.getColumn();
					//Element cellNode = doc.createElement("td");
					Element cellNode = doc.createElementNS("http://www.w3.org/1999/XSL/Format", 
					"fo:table-cell");
					row.appendChild(cellNode);

					// style
					//cellNode.setAttribute("border-style", "dashed");
					
					// td default - just from TableGrid for now
					// TODO .. get these right on a per table basis	
					if (tblBorders.getInsideH()!=null) {
						( new BorderTop(   tblBorders.getTop()    )).setXslFO(cellNode);
						( new BorderBottom(tblBorders.getBottom() )).setXslFO(cellNode);
					}
					if (tblBorders.getInsideV()!=null) { 
						( new BorderRight(tblBorders.getRight() )).setXslFO(cellNode);
						( new BorderLeft( tblBorders.getLeft()  )).setXslFO(cellNode);
					}						
					// Ensure empty cells have a sensible height
					cellNode.setAttribute("height", "5mm");
						
					// now override the defaults
					if (cell.getTcPr()!=null ) {
						StringBuffer inlineStyle =  new StringBuffer();
						Conversion.createFoAttributes(cell.getTcPr(), cellNode);				
					}
					
					
					if (cell.getExtraCols() > 0) {
						cellNode.setAttribute("number-columns-spanned", Integer.toString(cell
								.getExtraCols() + 1));
						
					}
					if (cell.getExtraRows() > 0) {
						cellNode.setAttribute("number-rows-spanned", Integer.toString(cell
								.getExtraRows() + 1));
					}
					// insert content into cell
					// skipping w:tc node itself, insert only its children
					if (cell.getContent() == null) {
						logger.warn("model cell had no contents!");
					} else {
						logger.debug("copying cell contents..");
						XmlUtils.treeCopy(cell.getContent().getChildNodes(),
								cellNode);
					}
				}
			}
		}
    return docfrag;
  }
}
