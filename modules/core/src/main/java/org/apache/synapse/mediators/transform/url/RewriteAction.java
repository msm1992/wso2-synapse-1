/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.mediators.transform.url;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.apache.synapse.MessageContext;

import java.net.URISyntaxException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a URL rewrite action. The action could be rewriting the entire URL
 * or rewriting a fragment of the URL.
 */
public class RewriteAction {

    private static final Log log = LogFactory.getLog(RewriteAction.class);

    public static final int ACTION_SET      = 0;
    public static final int ACTION_APPEND   = 1;
    public static final int ACTION_PREPEND  = 2;
    public static final int ACTION_REPLACE  = 3;
    public static final int ACTION_REMOVE   = 4;
    public static final int ACTION_REMOVE_QUERY_PARAM = 5;

    private String value;
    private SynapseXPath xpath;
    private String regex;
    private int fragmentIndex = URIFragments.FULL_URI;
    private int actionType = ACTION_SET;
    private boolean resolve = false;

    public void execute(URIFragments fragments,
                        MessageContext messageContext) throws URISyntaxException {

        String result;
        if (xpath != null) {
            result = xpath.stringValueOf(messageContext);
        } else {
            result = value;
        }

        if (fragmentIndex == URIFragments.FULL_URI) {
            URI uri;
            if (result != null) {
                uri = new URI(result);
                if (log.isTraceEnabled()) {
                    log.trace("Setting the URI to: " + result);
                }
            } else {
                uri = new URI("");
            }

            // Since the entire URL has been rewritten we need to reinit all the fragments
            fragments.setFragments(uri);

        } else if (fragmentIndex == URIFragments.PORT) {
            // When setting the port we must first convert the value into an integer
            if (result != null) {
                fragments.setPort(Integer.parseInt(result));
            } else {
                fragments.setPort(-1);
            }
        } else {
            String str;
            String currentValue = fragments.getStringFragment(fragmentIndex);
            if (currentValue == null) {
                currentValue = "";
            }

			switch (actionType) {
				case ACTION_PREPEND:
				    if (result != null) {
				        if (fragmentIndex == URIFragments.QUERY && !StringUtils.isEmpty(currentValue)) {
                            str = result.concat("&" + currentValue);
                        } else {
                            str = result.concat(currentValue);
                        }
                    } else {
				        str = "";
                    }
					break;
				case ACTION_APPEND:
					if (result != null) {
                        if (fragmentIndex == URIFragments.QUERY && !StringUtils.isEmpty(currentValue)) {
                            str = currentValue.concat("&" + result);
                        } else {
                            str = currentValue.concat(result);
                        }
					} else {
						str = "";
					}
					break;
				case ACTION_REPLACE:
					if (result != null) {
						str = currentValue.replaceAll(regex, result);
					} else {
						str = "";
					}
					break;
				case ACTION_REMOVE:
					str = null;
					break;
                case ACTION_REMOVE_QUERY_PARAM:
                    if (!StringUtils.isEmpty(currentValue)) {
                        String[] queryParams = currentValue.split("&");
                        List<String> queryParamList = new ArrayList<>(Arrays.asList(queryParams));
                        Iterator iterator = queryParamList.iterator();

                        Pattern pattern = Pattern.compile(result + "=.*");
                        Matcher matcher;
                        while (iterator.hasNext()) {
                            String s = (String)iterator.next();
                            matcher = pattern.matcher(s);
                            if (matcher.find()) {
                                iterator.remove();
                                break;
                            }
                        }
                        str = String.join("&", queryParamList);
                    } else {
                        str = "";
                    }
                    break;
				default:
					str = result;
			}

            if (resolve) {
                String pathParamRegex = "[^{}]*\\{([^{}]*)}";
                Pattern pattern =  Pattern.compile(pathParamRegex);
                Matcher matcher = pattern.matcher(str);
                while (matcher.find()) {
                    String pathParam = matcher.group(1);
                    String paramValue = (String) messageContext.getProperty(pathParam);
                    if (!StringUtils.isEmpty(paramValue)) {
                        str = str.replace("{" + pathParam + "}", paramValue);
                    } else {
                        log.error("Path parameter '" + pathParam + "' was not found in the provided URL postfix");
                        str = "";
                    }
                }
            }
			
            fragments.setStringFragment(fragmentIndex, str);
        }
    }

    public int getFragmentIndex() {
        return fragmentIndex;
    }

    public void setFragmentIndex(int fragmentIndex) {
        this.fragmentIndex = fragmentIndex;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public SynapseXPath getXpath() {
        return xpath;
    }

    public void setXpath(SynapseXPath xpath) {
        this.xpath = xpath;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public int getActionType() {
        return actionType;
    }

    public void setActionType(int actionType) {
        this.actionType = actionType;
    }

    public boolean isResolve() {
        return resolve;
    }

    public void setResolve(boolean resolve) {
        this.resolve = resolve;
    }
}
