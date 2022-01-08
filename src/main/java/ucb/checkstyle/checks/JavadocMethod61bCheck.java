////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2015 the original author or authors.
// Copyright (C) 2011-2021 P. N. Hilfinger (modifications).
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
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package ucb.checkstyle;

import antlr.collections.AST;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTagInfo;
import com.puppycrawl.tools.checkstyle.api.Scope;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtil;
import com.puppycrawl.tools.checkstyle.api.TextBlock;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck;
import com.puppycrawl.tools.checkstyle.checks.javadoc.AbstractTypeAwareCheck;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * Checks the Javadoc of a method or constructor.
 *
 * This version is an extension of the original JavadocMethodCheck by
 * Burn, Giles, and Sukhodoslky that permits a different way of
 * describing parameters:
 *   *  When the allowNarrativeParamTags flag is true, a parameter is
 *      considered to be mentioned if it appears in all uppercase in the
 *      text of the Javadoc comment, as well as when it appears as a
 *      @@param tag.  Default value: false.
 *   *  When the allowNarrativeReturnTags flag is true, a return value is
 *      taken to be described if any of the word "returns", "return",
 *      "returning", "yield", "yields", or "yielding" appears (case
 *      insensitively) in the text of the comment, as well as when it appears 
 *      in a @@return tag.  Default value: false.
 *   *  Any parameter whose name matches the regexp unusedParamFormat
 *      need not appear in the Javadoc comment. Default value: null,
 *      which matches nothing.
 *
 * @author Oliver Burn
 * @author Rick Giles
 * @author o_sukhodoslky
 * @author Paul Hilfinger
 * @version 1.0 (from JavadocMethodCheck in checkstyle-6.8.1)
 */
public class JavadocMethod61bCheck extends JavadocMethodCheck {

    /**
     * Controls whether to allow parameters to be described in running
     * text, written in all caps.
     */
    private boolean allowNarrativeParamTags;

    /**
     * Controls whether to allow return values of functions to be described
     * in running text, using the words "return", "returning", "returns",
     * "yield", "yields", or "yielding", in any case.
     */
    private boolean allowNarrativeReturnTags;

    /** Compiled regexp for unused parameters. */
    private Pattern unusedParamFormatRE;

    /**
     * The format of parameter names that need not be mentioned in a
     * Javadoc comment.
     */
    private String unusedParamFormat;

    /** List of annotations that could allow missed documentation. */
    private List<String> allowedAnnotations = Arrays.asList("Override");

    /** Method names that match this pattern do not require javadoc blocks. */
    private Pattern ignoreMethodNamesRegex;

    /**
     * Set regex for matching method names to ignore.
     * @param regex regex for matching method names.
     */
    public void setIgnoreMethodNamesRegex(String regex) {
        ignoreMethodNamesRegex = CommonUtil.createPattern(regex);
    }

    /**
     * Controls whether to allow descriptions of parameters to be
     * included in the main text of a Javadoc comment, written in all caps.
     * Defaults to false.
     *
     * @param aFlag a <code>Boolean</code> value
     */
    public void setAllowNarrativeParamTags(final boolean aFlag) {
        allowNarrativeParamTags = aFlag;
    }

    /**
     * Controls whether to allow return values of functions to be described
     * in running text, using the words "return", "returning", "returns",
     * "yield", "yields", or "yielding", in any case.
     * @param aFlag a <code>Boolean</code> value
     */
    public void setAllowNarrativeReturnTags(final boolean aFlag) {
        allowNarrativeReturnTags = aFlag;
    }

    /**
     * Set the format of parameter names that need not be commented.
     * Default value is null, matching no names.
     *
     * @param format a <code>String</code> value
     */
    public void setUnusedParamFormat(String format) {
        unusedParamFormat = format;
        unusedParamFormatRE = Pattern.compile(format);
    }

    @Override
    protected final void processAST(DetailAST ast) {
        if ((ast.getType() == TokenTypes.METHOD_DEF || ast.getType() == TokenTypes.CTOR_DEF)
            && getMethodsNumberOfLine(ast) <= minLineCount
            || hasAllowedAnnotations(ast)) {
            return;
        }
        final Scope theScope = calculateScope(ast);
        if (shouldCheck(ast, theScope)) {
            final FileContents contents = getFileContents();
            final TextBlock cmt = contents.getJavadocBefore(ast.getLineNo());

            if (cmt == null) {
                if (!isMissingJavadocAllowed(ast)) {
                    log(ast, MSG_JAVADOC_MISSING);
                }
            }
            else {
                checkComment(ast, cmt);
            }
        }
    }

    /**
     * Checks the Javadoc for a method.
     *
     * @param ast the token for the method
     * @param comment the Javadoc comment
     */
    private void checkComment(DetailAST ast, TextBlock comment) {
        final String[] commentLines = comment.getText();
        final int startLine = comment.getStartLineNo();
        final int startCol = comment.getStartColNo();
        final List<JavadocTag> tags =
            getMethodTags(commentLines, startLine, startCol);

        if (hasShortCircuitTag(ast, tags)) {
            return;
        }

        if (ast.getType() != TokenTypes.ANNOTATION_FIELD_DEF) {
            // Check for inheritDoc
            boolean hasInheritDocTag = false;
            for (JavadocTag jt : tags) {
                if (jt.isInheritDocTag()) {
                    hasInheritDocTag = true;
                    break;
                }
            }

            checkParams(tags, ast, commentLines, !hasInheritDocTag);
            checkThrowsTags(tags, getThrows(ast), !hasInheritDocTag);
            if (isFunction(ast)) {
                checkReturnTag(tags, ast.getLineNo(), commentLines,
                               !hasInheritDocTag);
            }
        }

        // Dump out all unused tags
        for (JavadocTag jt : tags) {
            if (!jt.isSeeOrInheritDocTag()) {
                log(jt.getLineNo(), MSG_UNUSED_TAG_GENERAL);
            }
        }
    }

    /** ??
     * Validates whether the Javadoc has a short circuit tag. Currently this is
     * the inheritTag. Any errors are logged.
     *
     * @param ast the construct being checked
     * @param tags the list of Javadoc tags associated with the construct
     * @return true if the construct has a short circuit tag.
     */
    private boolean hasShortCircuitTag(final DetailAST ast,
            final List<JavadocTag> tags) {
        // Check if it contains {@inheritDoc} tag
        if (tags.size() != 1
                || !tags.get(0).isInheritDocTag()) {
            return false;
        }

        // Invalid if private, a constructor, or a static method
        if (!JavadocTagInfo.INHERIT_DOC.isValidOn(ast)) {
            log(ast, MSG_INVALID_INHERIT_DOC);
        }

        return true;
    }

    /**
     * Computes the parameter nodes for a method.
     *
     * @param ast the method node.
     * @return the list of parameter nodes for ast.
     */
    private List<DetailAST> getParameters(DetailAST ast) {
        final DetailAST params = ast.findFirstToken(TokenTypes.PARAMETERS);
        final List<DetailAST> retVal = Lists.newArrayList();

        DetailAST child = params.getFirstChild();
        while (child != null) {
            if (child.getType() == TokenTypes.PARAMETER_DEF) {
                final DetailAST ident = child.findFirstToken(TokenTypes.IDENT);
                retVal.add(ident);
            }
            child = child.getNextSibling();
        }
        return retVal;
    }

    /** Pattern describing a possible word describing return of a value. */
    private static final Pattern RETURN_NARRATIVE_PATN =
        Pattern.compile("\\b(return|yield)(s|ing)?\\b",
                        Pattern.CASE_INSENSITIVE);

    /**
     * Returns true iff a word suggestive of returning appears in
     * commentText.
     *
     * @param commentText lines of text to search.
     * @return whether a narrative return description appears in commentText.
     */
    private boolean findReturnNarrative(final String[] commentText) {
        Matcher matcher = RETURN_NARRATIVE_PATN.matcher("");
        for (String line : commentText) {
            matcher.reset(line);
            if (matcher.find())
                return true;
        }
        return false;
    }

    /**
     * Checks that parameters and return value are properly commented on.
     * Removes parameter tags from aTags as a side effect.
     *
     * @param aTags the tags to check
     * @param aParent the node which takes the parameters
     * @param commentText the text of the comment, as an array of lines.
     * @param aReportExpectedTags whether we should report if do not find
     *            expected tag
     */
    private void checkParams(final List<JavadocTag> aTags,
                             final DetailAST aParent,
                             final String[] commentText,
                             boolean aReportExpectedTags) {
        boolean tagFound = false;
        boolean narrativeFound = false;

        for (DetailAST param : getParameters(aParent)) {
            JavadocTag tag = findRemoveParamTag(param.getText(), aTags);

            if (unusedParamFormatRE != null
                && unusedParamFormatRE.matcher(param.getText()).matches()) {
                continue;
            }

            if (tag != null) {
                tagFound = true;
            } else if (allowNarrativeParamTags
                       && findMention(param.getText(), commentText)) {
                narrativeFound = true;
            } else if (!allowMissingParamTags && aReportExpectedTags) {
                log(param, "javadoc.expectedTag",
                    JavadocTagInfo.PARAM.getText(), param.getText());
            }
        }

        for (DetailAST typeParam : CheckUtil.getTypeParameters(aParent)) {
            String tagName =
                typeParam.findFirstToken(TokenTypes.IDENT).getText();
            String tagText = "<" + tagName + ">";
            JavadocTag tag = findRemoveParamTag(tagText, aTags);

            if (unusedParamFormatRE != null
                && unusedParamFormatRE.matcher(tagName).matches()) {
                continue;
            }

            if (tag != null) {
                tagFound = true;
            } else if (allowNarrativeParamTags
                       && findMention(tagName, commentText)) {
                narrativeFound = true;
            } else if (!allowMissingParamTags && aReportExpectedTags) {
                log(typeParam, "javadoc.expectedTag",
                    JavadocTagInfo.PARAM.getText(), tagText);
            }
        }

        if (tagFound && narrativeFound) {
            log(aParent, "javadoc.mixedStyle");
        }

        final ListIterator<JavadocTag> tagIt = aTags.listIterator();
        while (tagIt.hasNext()) {
            JavadocTag tag = tagIt.next();
            if (tag.isParamTag()) {
                log(tag.getLineNo(), tag.getColumnNo(), "javadoc.unusedTag",
                    "@param", tag.getFirstArg());
                tagIt.remove();
            }
        }
    }

    /**
     * Checks for only one return tag. All return tags will be removed from the
     * supplied list.
     *
     * @param tags the tags to check
     * @param lineNo the line number of the expected tag
     * @param reportExpectedTags whether we should report if do not find
     *            expected tag
     */
    private void checkReturnTag(List<JavadocTag> tags, int lineNo,
                                final String[] commentText,
                                boolean reportExpectedTags) {
        // Loop over tags finding return tags. After the first one, report an
        // error.
        boolean found = false;
        final ListIterator<JavadocTag> it = tags.listIterator();
        while (it.hasNext()) {
            final JavadocTag jt = it.next();
            if (jt.isReturnTag()) {
                if (found) {
                    log(jt.getLineNo(), jt.getColumnNo(),
                        MSG_DUPLICATE_TAG,
                        JavadocTagInfo.RETURN.getText());
                }
                found = true;
                it.remove();
            }
        }

        // Handle there being no @return tags :- unless
        // the user has chosen to suppress these problems
        if (!found && !allowMissingReturnTag && reportExpectedTags
            && (!allowNarrativeReturnTags
                || !findReturnNarrative(commentText))) {
            log(lineNo, MSG_RETURN_EXPECTED);
        }
    }

    /**
     * Checks a set of tags for matching throws.
     *
     * @param tags the tags to check
     * @param aThrows the throws to check
     * @param aReportExpectedTags whether we should report if do not find
     *            expected tag
     */
    private void checkThrowsTags(List<JavadocTag> tags,
            List<ExceptionInfo> aThrows, boolean reportExpectedTags) {
        // Loop over the tags, checking to see they exist in the throws.
        final ListIterator<JavadocTag> tagIt = tags.listIterator();
        while (tagIt.hasNext()) {
            final JavadocTag tag = tagIt.next();

            if (!tag.isThrowsTag()) {
                continue;
            }

            tagIt.remove();

            // Loop looking for matching throw
            final String documentedEx = tag.getFirstArg();
            final Token token = new Token(tag.getFirstArg(), tag.getLineNo(), tag
                    .getColumnNo());
            final AbstractClassInfo documentedCI = createClassInfo(token,
                    getCurrentClassName());

            if (!exceptionMatchedByName(aThrows,
                                        documentedCI.getName().getText())
                && !exceptionMatchedByClass(aThrows,
                                            documentedCI.getClazz())) {
                // Handle extra JavadocTag.
                boolean reqd = true;
                if (allowUndeclaredRTE) {
                    reqd = !isUnchecked(documentedCI.getClazz());
                }

                if (reqd) {
                    log(tag.getLineNo(), tag.getColumnNo(),
                        "javadoc.unusedTag",
                        JavadocTagInfo.THROWS.getText(),
                        tag.getFirstArg());
                }
            }
        }

        if (!allowMissingThrowsTags && reportExpectedTags) {
            for (ExceptionInfo ei : aThrows) {
                if (!ei.isFound()) {
                    final Token fi = ei.getName();
                    log(fi.getLineNo(), fi.getColumnNo(),
                        "javadoc.expectedTag",
                        JavadocTagInfo.THROWS.getText(), fi.getText());
                }
            }
        }
    }

}














