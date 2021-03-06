/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.action;

import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.exception.UserLockoutException;
import com.liferay.portal.kernel.exception.UserPasswordException;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.model.Ticket;
import com.liferay.portal.kernel.model.TicketConstants;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.AuthTokenUtil;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.auth.session.AuthenticatedSessionManagerUtil;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.TicketLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.servlet.HttpMethods;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.security.pwd.PwdToolkitUtilThreadLocal;
import com.liferay.portal.struts.Action;
import com.liferay.portal.struts.model.ActionForward;
import com.liferay.portal.struts.model.ActionMapping;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author Brian Wing Shun Chan
 * @author Mika Koivisto
 */
public class UpdatePasswordAction implements Action {

	@Override
	public ActionForward execute(
			ActionMapping actionMapping, HttpServletRequest request,
			HttpServletResponse response)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		Ticket ticket = getTicket(request);

		if ((ticket != null) &&
			StringUtil.equals(request.getMethod(), HttpMethods.GET)) {

			resendAsPost(request, response);

			return null;
		}

		request.setAttribute(WebKeys.TICKET, ticket);

		String cmd = ParamUtil.getString(request, Constants.CMD);

		if (Validator.isNull(cmd)) {
			if (ticket != null) {
				User user = UserLocalServiceUtil.getUser(ticket.getClassPK());

				try {
					UserLocalServiceUtil.checkLockout(user);

					UserLocalServiceUtil.updatePasswordReset(
						user.getUserId(), true);
				}
				catch (UserLockoutException ule) {
					SessionErrors.add(request, ule.getClass(), ule);
				}
			}

			return actionMapping.getActionForward("portal.update_password");
		}

		try {
			updatePassword(request, response, themeDisplay, ticket);

			String redirect = ParamUtil.getString(request, WebKeys.REFERER);

			if (Validator.isNotNull(redirect)) {
				redirect = PortalUtil.escapeRedirect(redirect);
			}

			if (Validator.isNull(redirect)) {
				redirect = themeDisplay.getPathMain();
			}

			response.sendRedirect(redirect);

			return null;
		}
		catch (Exception e) {
			if (e instanceof UserPasswordException) {
				SessionErrors.add(request, e.getClass(), e);

				return actionMapping.getActionForward("portal.update_password");
			}
			else if (e instanceof NoSuchUserException ||
					 e instanceof PrincipalException) {

				SessionErrors.add(request, e.getClass());

				return actionMapping.getActionForward("portal.error");
			}

			PortalUtil.sendError(e, request, response);

			return null;
		}
	}

	protected Ticket getTicket(HttpServletRequest request) {
		String ticketKey = ParamUtil.getString(request, "ticketKey");

		if (Validator.isNull(ticketKey)) {
			return null;
		}

		try {
			Ticket ticket = TicketLocalServiceUtil.fetchTicket(ticketKey);

			if ((ticket == null) ||
				(ticket.getType() != TicketConstants.TYPE_PASSWORD)) {

				return null;
			}

			if (!ticket.isExpired()) {
				return ticket;
			}

			TicketLocalServiceUtil.deleteTicket(ticket);
		}
		catch (Exception e) {
		}

		return null;
	}

	protected boolean isValidatePassword(HttpServletRequest request) {
		HttpSession session = request.getSession();

		Boolean setupWizardPasswordUpdated = (Boolean)session.getAttribute(
			WebKeys.SETUP_WIZARD_PASSWORD_UPDATED);

		if ((setupWizardPasswordUpdated != null) &&
			setupWizardPasswordUpdated) {

			return false;
		}

		return true;
	}

	protected void resendAsPost(
			HttpServletRequest request, HttpServletResponse response)
		throws IOException {

		response.setHeader(
			"Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Expires", "0");
		response.setHeader("Pragma", "no-cache");

		PrintWriter printWriter = response.getWriter();

		Map<String, String[]> parameterMap = request.getParameterMap();

		StringBundler sb = new StringBundler(7 + parameterMap.size() * 5);

		sb.append("<html><body onload=\"document.fm.submit();\">");
		sb.append("<form action=\"");
		sb.append(PortalUtil.getPortalURL(request));
		sb.append("/c/portal/update_password\" method=\"post\" name=\"fm\">");

		for (String name : parameterMap.keySet()) {
			String value = ParamUtil.getString(request, name);

			sb.append("<input name=\"");
			sb.append(HtmlUtil.escapeAttribute(name));
			sb.append("\" type=\"hidden\" value=\"");
			sb.append(HtmlUtil.escapeAttribute(value));
			sb.append("\"/>");
		}

		sb.append("<noscript>");
		sb.append("<input type=\"submit\" value=\"Please continue here...\"/>");
		sb.append("</noscript></form></body></html>");

		printWriter.write(sb.toString());

		printWriter.close();
	}

	protected void updatePassword(
			HttpServletRequest request, HttpServletResponse response,
			ThemeDisplay themeDisplay, Ticket ticket)
		throws Exception {

		AuthTokenUtil.checkCSRFToken(
			request, UpdatePasswordAction.class.getName());

		long userId = 0;

		if (ticket != null) {
			userId = ticket.getClassPK();
		}
		else {
			userId = themeDisplay.getUserId();
		}

		String password1 = request.getParameter("password1");
		String password2 = request.getParameter("password2");
		boolean passwordReset = false;

		boolean previousValidate = PwdToolkitUtilThreadLocal.isValidate();

		try {
			boolean currentValidate = isValidatePassword(request);

			PwdToolkitUtilThreadLocal.setValidate(currentValidate);

			UserLocalServiceUtil.updatePassword(
				userId, password1, password2, passwordReset);
		}
		finally {
			PwdToolkitUtilThreadLocal.setValidate(previousValidate);
		}

		if (ticket != null) {
			TicketLocalServiceUtil.deleteTicket(ticket);

			UserLocalServiceUtil.updatePasswordReset(userId, false);
		}

		User user = UserLocalServiceUtil.getUser(userId);

		Company company = CompanyLocalServiceUtil.getCompanyById(
			user.getCompanyId());

		String login = null;

		String authType = company.getAuthType();

		if (authType.equals(CompanyConstants.AUTH_TYPE_EA)) {
			login = user.getEmailAddress();
		}
		else if (authType.equals(CompanyConstants.AUTH_TYPE_SN)) {
			login = user.getScreenName();
		}
		else if (authType.equals(CompanyConstants.AUTH_TYPE_ID)) {
			login = String.valueOf(userId);
		}

		AuthenticatedSessionManagerUtil.login(
			request, response, login, password1, false, null);
	}

}