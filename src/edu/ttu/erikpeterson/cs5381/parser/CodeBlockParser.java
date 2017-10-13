package edu.ttu.erikpeterson.cs5381.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class CodeBlockParser {

    private static final Pattern CLASS_PATTERN = Pattern.compile("\\sclass\\s");
    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile("\\s*(synchronized)\\s*\\(");

    // Pattern for creating and submitting a future
    private static final Pattern FUTURE_SUBMISSION = Pattern.compile("Future\\s*<.*>.*submit\\s*\\(");

    // Because the method pattern might match loops (for, while, etc.) we need to dispose of them first
    // Note that a for loop is handled in code because our parsing doesn't capture all of it (just the increment part after the last ';')
    private static final Pattern FOR_EACH_PATTERN = Pattern.compile("for\\s\\(.*:.*\\)");
    private static final Pattern WHILE_PATTERN = Pattern.compile("(while)\\s*\\(");
    private static final Pattern DO_PATTERN = Pattern.compile("^do$");

    private static final Pattern TRY_PATTERN = Pattern.compile("^try$");
    private static final Pattern CATCH_PATTERN = Pattern.compile("^catch[\\s]*\\(");
    private static final Pattern FINALLY_PATTERN = Pattern.compile("^finally$");

    // Attempts to match a method. Because this could well match while and synchronized blocks, it needs to happen last
    private static final Pattern METHOD_PATTERN = Pattern.compile("(public|private|protected)?\\s*(static)?[\\w\\s]*([\\w\\[\\]<>\\s,]*)");

    /**
     * @param file File to parse
     * @return The blocks in this file
     */
    public static LinkedList<CodeBlock> parse(File file) throws FileNotFoundException, BlockParsingException {
        LinkedList<CodeBlock> codeBlocks = new LinkedList<>();

        String contents = new Scanner(file).useDelimiter("\\Z").next();

        int position = 0;
        while (position < contents.length()) {
            CodeBlock codeBlock = findBlock(contents, codeBlocks, position);
            if ( codeBlock == null)
            {
                return codeBlocks;
            }
            position = codeBlock.getEndPosition() + 1;
        }

        return codeBlocks;
    }


    /**
     * Recursively find code blocks
     *
     * @param contents contents of the Java file
     * @param codeBlocks List of code blocks--expect stuff to be added to it!
     * @param startPosition Where to start looking in the file
     * @return All class code blocks (methods and whatnot are held internally)
     */
    private static CodeBlock findBlock(String contents, LinkedList<CodeBlock> codeBlocks, int startPosition) throws BlockParsingException {
        int numBlocksToStart = codeBlocks.size();
        int firstOpenBrace = contents.indexOf('{', startPosition);
        int firstCloseBrace = contents.indexOf('}', startPosition);
        if (firstOpenBrace < 0 || firstCloseBrace < firstOpenBrace) {
            // No more blocks
            return null;
        }

        // Grab the info for this block (the stuff just before the '}' and after the previous ';' or '}'
        String blockInfo;
        // Note that for loops (which include ';'s will be truncated here, but we don't need to identify them... :)
        int previousSemicolonPosition = contents.lastIndexOf(';', firstOpenBrace-1);
        int previousOpenBracePosition = contents.lastIndexOf('{', firstOpenBrace-1);
        int previousCloseBracePosition = contents.lastIndexOf('}', firstOpenBrace-1);
        int mostRecentPosition = Math.max(Math.max(previousSemicolonPosition, previousOpenBracePosition), previousCloseBracePosition)+1;
        if (mostRecentPosition < 0) {
            blockInfo = contents.substring(0, startPosition);
        } else if ( firstOpenBrace == mostRecentPosition)
        {
            blockInfo = "";
        } else {
            blockInfo = contents.substring(mostRecentPosition+1, firstOpenBrace);
        }
        int blockInfoStart = startPosition-blockInfo.length();
        blockInfo = blockInfo.trim();

        int newStartPosition = firstOpenBrace + 1;

        // See if there are blocks internal to us
        int nextOpenParen = contents.indexOf('{', newStartPosition);
        int nextCloseParen = contents.indexOf('}', newStartPosition);

        if ( nextOpenParen > nextCloseParen)
        {
            // We are a self-contained block
            String blockContents = contents.substring(firstOpenBrace + 1, nextCloseParen);
            CodeBlockType blockType = getBlockType(contents, blockInfo, blockInfoStart);
            CodeBlock codeBlock = new CodeBlock(blockInfo,
                                                blockType,
                                                blockContents,
                                                blockInfoStart,
                                                nextCloseParen);
            if ( blockType == CodeBlockType.CLASS)
            {
                codeBlocks.add(numBlocksToStart, codeBlock);
            }
            return codeBlock;
        }

        List<CodeBlock> subCodeBlocks = new ArrayList<>();
        // Now recursively look for internal code blocks
        while (true) {
            CodeBlock internalCodeBlock = findBlock(contents, codeBlocks, newStartPosition);
            if (internalCodeBlock == null) {
                // No more internal blocks
                break;
            } else {
                subCodeBlocks.add(internalCodeBlock);
                // Need to look for more internal blocks
                newStartPosition = internalCodeBlock.getEndPosition() + 1;
            }
        }

        // Now that we've found all the internal code blocks, the next '}' is the end of our block
        // Assuming well-formed code, and no '{' or '}' in comments....
        int closeOfOurBlock = contents.indexOf('}', newStartPosition);
        String blockContents = contents.substring(firstOpenBrace + 1, closeOfOurBlock);

        // Add our code block so that it's before the sub-blocks (aka after everything that came before us)
        CodeBlockType blockType = getBlockType(contents, blockInfo, blockInfoStart);
        CodeBlock ourCodeBlock = new CodeBlock(blockInfo,
                                               blockType,
                                               blockContents,
                                               blockInfoStart,
                                               closeOfOurBlock);
        ourCodeBlock.addCodeBlocks(subCodeBlocks);
        if ( blockType == CodeBlockType.CLASS)
        {
            codeBlocks.add(numBlocksToStart, ourCodeBlock);
        }
        return ourCodeBlock;
    }

    private static CodeBlockType getBlockType(String fullText, String blockInfo, int blockInfoPosition) throws BlockParsingException
    {
        // Check for class " ... class ... "
        if ( CLASS_PATTERN.matcher(blockInfo).find())
        {
            return CodeBlockType.CLASS;
        }

        if ( SYNCHRONIZED_PATTERN.matcher(blockInfo).find())
        {
            return CodeBlockType.SYNCHRONIZED;
        }

        // Our parser will catch only the "i++)" part of a for loop because we look back to the last ';'
        if ( blockInfo.contains(")") && !blockInfo.contains("("))
        {
            return CodeBlockType.CODE_BLOCK;
        }

        if ( FUTURE_SUBMISSION.matcher(blockInfo).find())
        {
            return CodeBlockType.THREAD_ENTRY;
        }

        // TODO: May want to split out try/catch/finally at some point
        // (e.g. to check if locks done in a try are undone in a finally)
        if ( FOR_EACH_PATTERN.matcher(blockInfo).find() ||
             WHILE_PATTERN.matcher(blockInfo).find() ||
             DO_PATTERN.matcher(blockInfo).find() ||
             TRY_PATTERN.matcher(blockInfo).find() ||
             CATCH_PATTERN.matcher(blockInfo).find() ||
             FINALLY_PATTERN.matcher(blockInfo).find())
        {
            return CodeBlockType.CODE_BLOCK;
        }

        if ( METHOD_PATTERN.matcher(blockInfo).find()) {
            return CodeBlockType.METHOD;
        }

        throw new BlockParsingException("Unknown block type: " + blockInfo);
    }

}
