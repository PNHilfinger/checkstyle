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

package ucb.checkstyle.checks;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TextBlock;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
@FileStatefulCheck
public class JavadocMethod61bCheck extends JavadocMethodCheck {

    /** Compiled regexp @inheritDoc tags. */
    private static final Pattern MATCH_INHERITDOC =
        CommonUtil.createPattern("\\{\\s*@(inheritDoc)\\s*\\}");

    /** Matches potential narrative mention of parameter name. */
    private static final Pattern NARRATIVE_PARAM_RE =
        CommonUtil.createPattern("\\b[A-Z_][A-Z0-9_]*\\b");

    /** Pattern describing a possible word describing return of a value. */
    private static final Pattern NARRATIVE_RETURN_RE =
        CommonUtil.createPattern("\\b(return|yield)(s|ing)?\\b",
                                 Pattern.CASE_INSENSITIVE);

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

    /**
     * The AST that is currently being processed.  This is not elegant, but
     * is expedient for avoiding more modification of JavadocMethod.
     */
    protected DetailAST currentAst;

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
        if (format == null) {
            unusedParamFormatRE = null;
        } else {
            unusedParamFormatRE = Pattern.compile(format);
        }
    }

    @Override
    protected void processAST(DetailAST ast) {
        currentAst = ast;
        super.processAST(ast);
    }

    @Override
    protected List<JavadocTag> getMethodTags(TextBlock comment) {
        List<JavadocTag> tags = super.getMethodTags(comment);
        if (unusedParamFormatRE != null) {
            for (Iterator<JavadocTag> it = tags.iterator(); it.hasNext();) {
                JavadocTag tag = it.next();
                if (tag.isParamTag()
                    && unusedParamFormatRE.matcher(tag.getFirstArg()).matches()) {
                    it.remove();
                }
            }
        }
        if (!allowNarrativeParamTags && !allowMissingParamTags) {
            return tags;
        }
        boolean hasParamTags, hasReturnTag;
        hasParamTags = hasReturnTag = false;
    
        for (JavadocTag tag : tags) {
            hasParamTags |= tag.isParamTag();
            hasReturnTag |= tag.isReturnTag();
        }

        if (allowNarrativeReturnTags && !hasReturnTag) {
            for (String line : comment.getText()) {
                if (NARRATIVE_RETURN_RE.matcher(line).find()) {
                    tags.add(new JavadocTag(comment.getStartLineNo(), 0,
                                                  "return"));
                    break;
                }
            }
        }

        if (allowNarrativeParamTags && !hasParamTags) {
            Matcher potentialParams = NARRATIVE_PARAM_RE.matcher("");
            ArrayList<String> narrativeParams = new ArrayList<>();
            for (String line : comment.getText()) {
                potentialParams.reset(line);
                while (potentialParams.find()) {
                    String p = potentialParams.group();
                    for (DetailAST param : getParameters(currentAst)) {
                        if (p.compareToIgnoreCase(param.getText()) == 0) {
                            if (!narrativeParams.contains(param.getText())) {
                                narrativeParams.add(param.getText());
                            }
                            break;
                        }
                    }
                    for (DetailAST typeParam :
                             CheckUtil.getTypeParameters(currentAst)) {
                        String tagName =
                            typeParam.findFirstToken(TokenTypes.IDENT).getText();
                        if (p.compareToIgnoreCase(tagName) == 0) {
                            String paramName = "<" + tagName + ">";
                            if (!narrativeParams.contains(paramName)) {
                                narrativeParams.add(paramName);
                            }
                            break;
                        }
                    }
                }
            }
            for (String paramName : narrativeParams) {
                tags.add(new JavadocTag(comment.getStartLineNo(), 0, "param",
                                        paramName));
            }
        }

        return tags;
    }

    /**
     * Computes the parameter nodes for a method.
     *
     * @param ast the method node.
     * @return the list of parameter nodes for ast.
     */
    @Override
    protected List<DetailAST> getParameters(DetailAST ast) {
        List<DetailAST> params = super.getParameters(ast);
        if (unusedParamFormatRE != null) {
            for (Iterator<DetailAST> it = params.iterator(); it.hasNext();) {
                DetailAST param = it.next();
                if (unusedParamFormatRE.matcher(param.getText()).matches()) {
                    it.remove();
                }
            }
        }
        return params;
    }

}







