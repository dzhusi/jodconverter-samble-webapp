//
// JODConverter - Java OpenDocument Converter
// Copyright (C) 2004-2007 - Mirko Nasato <mirko@artofsolving.com>
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
// http://www.gnu.org/copyleft/lesser.html
//
package org.artofsolving.jodconverter.sample.web;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.artofsolving.jodconverter.document.DocumentFormat;
import org.artofsolving.jodconverter.document.DocumentFormatRegistry;
import org.artofsolving.jodconverter.office.OfficeException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This servlet offers a document converter service suitable for remote invocation
 * by HTTP clients written in any language.
 * <p>
 * To be valid a request to service must:
 * <ul>
 * <li>use the POST method and send the input document data as the request body</li>
 * <li>specify the correct <b>Content-Type</b> of the input document</li>
 * <li>specify an <b>Accept</b> header with the mime-type of the desired output</li>
 * </ul>
 * <p>
 * As a very simple example, a request to convert a text document into PDF would
 * look something like
 * <pre>
 * POST /jooconverter/service HTTP/1.1
 * Host: x.y.z
 * Content-Type: text/plain
 * Accept: application/pdf
 *
 * Hello world!
 * </pre>
 */
public class ConverterServiceServlet extends HttpServlet {

    private static final long serialVersionUID = -6698481065322400366L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        WebappContext webappContext = WebappContext.get(getServletContext());
        OfficeDocumentConverter converter = webappContext.getDocumentConverter();
        DocumentFormatRegistry registry = converter.getFormatRegistry();

        String inputMimeType = request.getHeader("inputMime");
        if (inputMimeType == null) {
            throw new IllegalArgumentException("Content-Type not set in request");
        }

        DocumentFormat inputFormat = registry.getFormatByMediaType(inputMimeType);
        if (inputFormat == null) {
            throw new IllegalArgumentException("unsupported input mime-type: " + inputMimeType);
        }


        String outputMimeType = request.getHeader("outputMime");
        if (outputMimeType == null) {
            throw new IllegalArgumentException("Accept header not set in request");
        }
        DocumentFormat outputFormat = registry.getFormatByMediaType(outputMimeType);
        if (outputFormat == null) {
            throw new IllegalArgumentException("unsupported output mime-type: " + outputMimeType);
        }

        response.setContentType(outputFormat.getMediaType());
        //response.setContentLength(???); - should cache result in a buffer first

        // Get uploaded file
        ServletFileUpload fileUpload = webappContext.getFileUpload();
        FileItem uploadedFile;
        try {
            uploadedFile = getUploadedFile(fileUpload, request);
        } catch (FileUploadException fileUploadException) {
            throw new ServletException(fileUploadException);
        }

        // Generate temporary files and convert
        File inputFile = null;
        File outputFile = null;
        try {
            inputFile = File.createTempFile("document", "." + inputFormat.getExtension());
            writeUploadedFile(uploadedFile, inputFile);

            outputFile = File.createTempFile("document", "." + outputFormat.getExtension());
            converter.convert(inputFile, outputFile, outputFormat);
            InputStream outputFileStream = null;
            try {
                outputFileStream = new FileInputStream(outputFile);
                IOUtils.copy(outputFileStream, response.getOutputStream());
            } finally {
                IOUtils.closeQuietly(outputFileStream);
            }
        } catch (IOException ioException) {
            throw new ServletException("conversion failed", ioException);
        } catch (OfficeException officeException) {
            throw new ServletException("conversion failed", officeException);
        } finally {
            if (inputFile != null) {
            }
            if (outputFile != null) {
                inputFile.delete();
                outputFile.delete();
            }
        }
    }

    private void writeUploadedFile(FileItem uploadedFile, File destinationFile) throws ServletException {
        try {
            uploadedFile.write(destinationFile);
        } catch (Exception exception) {
            throw new ServletException("error writing uploaded file", exception);
        }
        uploadedFile.delete();
    }

    private FileItem getUploadedFile(ServletFileUpload fileUpload, HttpServletRequest request) throws FileUploadException {
        @SuppressWarnings("unchecked")
        List<FileItem> fileItems = fileUpload.parseRequest(request);
        for (FileItem fileItem : fileItems) {
            if (!fileItem.isFormField()) {
                return fileItem;
            }
        }
        return null;
    }

}