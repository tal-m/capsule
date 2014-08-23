/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
/*
 * *****************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Sonatype, Inc. - initial API and implementation
 ******************************************************************************
 */
package capsule;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

/**
 * A simplistic transfer listener that logs uploads/downloads to the console.
 */
public class ConsoleTransferListener extends AbstractTransferListener {
    private final PrintStream out;
    private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<TransferResource, Long>();
    private int lastLength;
    private final boolean verbose;

    public ConsoleTransferListener(boolean verbose, PrintStream out) {
        this.out = out;
        this.verbose = verbose;
    }

    @Override
    public void transferInitiated(TransferEvent event) {
        final String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";
        verbose(message + ": " + event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
    }

    @Override
    public void transferProgressed(TransferEvent event) {
        final TransferResource resource = event.getResource();
        downloads.put(resource, event.getTransferredBytes());

        final StringBuilder buffer = new StringBuilder(64);

        for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
            long total = entry.getKey().getContentLength();
            long complete = entry.getValue();

            buffer.append(getStatus(complete, total)).append("  ");
        }

        final int pad = lastLength - buffer.length();
        lastLength = buffer.length();
        pad(buffer, pad);
        buffer.append('\r');

        out.print(buffer);
    }

    private String getStatus(long complete, long total) {
        if (total >= 1024)
            return toKB(complete) + "/" + toKB(total) + " KB ";
        else if (total >= 0)
            return complete + "/" + total + " B ";
        else if (complete >= 1024)
            return toKB(complete) + " KB ";
        else
            return complete + " B ";
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        transferCompleted(event);
        if (!verbose)
            return;
        
        final TransferResource resource = event.getResource();
        final long contentLength = event.getTransferredBytes();
        if (contentLength >= 0) {
            final String type = (event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded");
            final String len = contentLength >= 1024 ? toKB(contentLength) + " KB" : contentLength + " B";

            String throughput = "";
            long duration = System.currentTimeMillis() - resource.getTransferStartTime();
            if (duration > 0) {
                long bytes = contentLength - resource.getResumeOffset();
                DecimalFormat format = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ENGLISH));
                double kbPerSec = (bytes / 1024.0) / (duration / 1000.0);
                throughput = " at " + format.format(kbPerSec) + " KB/sec";
            }

            println(type + ": " + resource.getRepositoryUrl() + resource.getResourceName() + " (" + len + throughput + ")");
        }
    }

    @Override
    public void transferFailed(TransferEvent event) {
        transferCompleted(event);

        if (!(event.getException() instanceof MetadataNotFoundException)) {
            println("Transfer failed: " + event.getException() + (verbose ? "" : " (for stack trace, run with -Dcapsule.log=verbose)"));
            if (verbose)
                event.getException().printStackTrace(out);
        }
    }

    @Override
    public void transferCorrupted(TransferEvent event) {
        println("Transfer corrupted: " + event.getException() + (verbose ? "" : " (for stack trace, run with -Dcapsule.log=verbose)"));
        if (verbose)
            event.getException().printStackTrace(out);
    }

    private void transferCompleted(TransferEvent event) {
        downloads.remove(event.getResource());

        final StringBuilder buffer = new StringBuilder(64);
        pad(buffer, lastLength);
        buffer.append('\r');
        out.print(buffer);
    }

    private static long toKB(long bytes) {
        return (bytes + 1023) / 1024;
    }

    private static void pad(StringBuilder buffer, int spaces) {
        final String block = "                                        ";
        while (spaces > 0) {
            int n = Math.min(spaces, block.length());
            buffer.append(block, 0, n);
            spaces -= n;
        }
    }

    private void println(String str) {
        out.println(str);
    }

    private void verbose(String str) {
        if (verbose)
            println(str);
    }
}