#parse ("definitions.vm")
<%@ include file="/init.jsp" %>

<link href="<%= billboardCss %>" rel="stylesheet">

<div id="<portlet:namespace />-billboard">
	<h1>An example from billboard.js</h1>

	<h2>Default charts</h2>

	<div id="<portlet:namespace />-BarChart"></div>
	<div id="<portlet:namespace />-StepChart"></div>
	<div id="<portlet:namespace />-LineChart"></div>
	<div id="<portlet:namespace />-AreaChart"></div>
	<div id="<portlet:namespace />-SplineChart"></div>
	<div id="<portlet:namespace />-StackedAreaChart"></div>

	<h2>D3 custom charts</h2>
	<style>
		.links line {
		stroke: #999;
		stroke-opacity: 0.6;
		}
		.nodes circle {
		stroke: #fff;
		stroke-width: 1.5px;
		}
	</style>

	<svg height="600" id="<portlet:namespace />-D3Graph" width="960"></svg>
</div>

<aui:script require="<%= mainRequire %>">
	main.default('<portlet:namespace />');
</aui:script>