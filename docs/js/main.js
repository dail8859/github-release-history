// I don't actually like these colors :(
var colorArray = ['75,192,192', '255,170,100', '255,203,100', '92,126,203'];
var apiRoot = "https://api.github.com/";

Date.prototype.yyyymmdd = function() {
	var yyyy = this.getUTCFullYear().toString();
	var mm = (this.getUTCMonth()+1).toString(); // getMonth() is zero-based
	var dd  = this.getUTCDate().toString();
	return yyyy + "-" + (mm[1]?mm:"0"+mm[0]) + "-" + (dd[1]?dd:"0"+dd[0]); // padding
};

function getQueryVariable(variable) {
	var query = window.location.search.substring(1);
	var vars = query.split("&");
	for(var i = 0; i < vars.length; i++) {
		var pair = vars[i].split("=");
		if(pair[0] == variable) {
			return pair[1];
		}
	}
	return "";
}

// validate the user input
function validateInput() {
	if ($("#username").val().length > 0 && $("#repository").val().length > 0) {
		$("#get-stats-button").prop("disabled", false);
	} else {
		$("#get-stats-button").prop("disabled", true);
	}
}

// Callback function for getting user repositories
function getUserRepos() {
	var user = $("#username").val();
	var autoComplete = $('#repository').typeahead();
	var repoNames = [];

	var url = apiRoot + "users/" + user + "/repos";
	$.getJSON(url, function(data) {
		$.each(data, function(index, item) {
			repoNames.push(item.name);
		});
	});

	autoComplete.data('typeahead').source = repoNames;
}

// Display the stats
function showStats(data) {
	var err = false;
	var errMessage = '';

	if(data.status == 404) {
		err = true;
		errMessage = "The project does not exist!";
	}

	if(data.length == 0) {
		err = true;
		errMessage = "There are no releases for this project";
	}

	var html = '';

	if(err) {
		html = "<div class='col-md-6 col-md-offset-3 error output'>" + errMessage + "</div>";
	} else {
		html += "<div class='col-md-6 col-md-offset-3 output'>";
		var latest = true;
		var totalDownloadCount = 0;

		$.each(data, function(index, item) {
			var releaseTag = item.tag_name;
			var releaseURL = item.html_url;
			var releaseAssets = item.assets;
			var hasAssets = releaseAssets.length != 0;
			var releaseAuthor = item.author;
			var publishDate = item.published_at.split("T")[0];

			if(latest) {
				html += "<div class='row release latest-release'>" +
					"<h2><a href='" + releaseURL + "' target='_blank'>" +
					"<span class='glyphicon glyphicon-tag'></span>&nbsp&nbsp" +
					"Latest Release: " + releaseTag +
					"</a></h2><hr class='latest-release-hr'>";
				latest = false;
			} else {
				html += "<div class='row release'>" +
					"<h4><a href='" + releaseURL + "' target='_blank'>" +
					"<span class='glyphicon glyphicon-tag'></span>&nbsp&nbsp" +
					releaseTag +
					"</a></h4><hr class='release-hr'>";
			}

			html += "<h4><span class='glyphicon glyphicon-info-sign'></span>&nbsp&nbsp" +
				"Release Info:</h4>";

			html += "<ul>";

			html += "<li><span class='glyphicon glyphicon-user'></span>&nbsp&nbspRelease Author: " +
				"<a href='" + releaseAuthor.html_url + "'>" + releaseAuthor.login  +"</a><br></li>";

			html += "<li><span class='glyphicon glyphicon-calendar'></span>&nbsp&nbspPublished on: " +
				publishDate + "</li>";

			html += "</ul>";

			if(hasAssets) {
				html += "<h4><span class='glyphicon glyphicon-download'></span>" +
					"&nbsp&nbspDownload Info: </h4>";

				html += "<ul>";
				$.each(releaseAssets, function(index, asset) {
					var assetSize = (asset.size / 1000000.0).toFixed(2);
					var lastUpdate = asset.updated_at.split("T")[0];
					html += "<li>" + asset.name + " (" + assetSize + "MB) - Downloaded " +
						asset.download_count + " times.<br><i>Last updated on " + lastUpdate + "</i></li>";
					totalDownloadCount += asset.download_count;
				});
				html += "</ul>";
			}
			html += "</div>";
		});

		if(totalDownloadCount > 0) {
			totalDownloadCount = totalDownloadCount.toString().replace(/(\d)(?=(\d{3})+$)/g, '$1,');
			html += "<div class='row total-downloads'>";
			html += "<h2><span class='glyphicon glyphicon-download'></span>" +
				"&nbsp&nbspTotal Downloads</h2> ";
			html += "<span>" + totalDownloadCount + "</span>";
			html += "</div>";
		}

		html += "</div>";
	}

	var resultDiv = $("#stats-result");
	resultDiv.hide();
	resultDiv.html(html);
	$("#loader-gif").hide();
	resultDiv.slideDown();
}


var stats = null;
var myLineChart = null;
// Callback function for getting release stats
function getGraphData() {
	var user = $("#username").val();
	var repository = $("#repository").val();

	var url = apiRoot + "repos/dail8859/github-release-history/contents/data/" + user + "/" + repository + ".json";
	//var url = "data/" + user + "/" + repository + ".json";
	//console.log(url)
	$.getJSON(url, function(blob) {
		stats = JSON.parse(atob(blob.content));
		$("#loader-gif").hide();

		var latest = new Date("1900-01-01");
		var release_id = 0;
		for (var key in stats.releases) {
			var created_at = new Date(stats.releases[key].created_at);
			if(created_at > latest) {
				latest = created_at;
				release_id = key;
			}
		}

		// Populate the dropdown list
		$("#originSelect").find('option').remove();
		for (var key in stats.releases) {
			var name = stats.releases[key].name;
			if (key == release_id) name = name.concat(" (latest release)");
			$("#originSelect").prepend($('<option>', {value : stats.releases[key].name}).text(name));
		}
		$("#originSelect").val($("#originSelect option:first").val());
		$("#stats-result").show();
		createGraph();
	}).fail(function(jqXHR, textStatus, errorThrown) {
		$("#loader-gif").hide();

		// Didn't find the data in the repository
		// Technically this could have also happened if a rate limit was hit
		// But we'll let the next request handle that case

		// Let's see if the repo even exists at all
		var url = apiRoot + "repos/" + user + "/" + repository + "/releases";
		$.getJSON(url, function(blob) {
			// It is a valid repo but hasn't been set up to be tracked yet
			var html = "<div class='col-md-6 col-md-offset-3 release'><h4><span class='glyphicon glyphicon-info-sign'></span>&nbsp&nbspThat repository hasn't been set up to have the download count tracked yet.</h4>";
			html += "<p>&nbsp</p><p>It's simple to start tracking it. Check the documentation to open an issue or pull request.</p>";
			html += "<p>&nbsp</p><p><i>Just show me an <a href='index.html?username=dail8859&repository=DoxyIt'>example</a>!</i></p></div>";
			var resultDiv = $("#stats-result");
			resultDiv.hide();
			resultDiv.html(html);
			resultDiv.slideDown();
		}).fail(function(jqXHR, textStatus, errorThrown) {
			var html = "";
			if (jqXHR.status == 404) {
				// Can't find the project on Github at all
				html = "<div class='col-md-6 col-md-offset-3 error output'><h4><span class='glyphicon glyphicon-exclamation-sign'></span>&nbsp&nbspThat project does not exist!</h4>";
				html += "<p><i>Just show me an <a href='index.html?username=dail8859&repository=DoxyIt'>example</a>!</i></p></div>";
			}
			else if (jqXHR.status == 403) {
				// Something bad happened. Probably a rate limit
				html = "<div class='col-md-6 col-md-offset-3 error output'><h4><span class='glyphicon glyphicon-exclamation-sign'></span>&nbsp&nbspGithub needs you to take a time out!</h4>";
				html += "<p>&nbsp</p>" + jqXHR.responseJSON.message + "</div>";
			}

			var resultDiv = $("#stats-result");
			resultDiv.hide();
			resultDiv.html(html);
			resultDiv.slideDown();
		});
	});
}

function createGraph() {
	var user = $("#username").val();
	var repository = $("#repository").val();
	var release_name = $("#originSelect").val();

	// Find the selected release
	var release_id = 0;
	for (var key in stats.releases) {
		if(stats.releases[key].name == release_name) {
			release_id = key;
			break;
		}
	}

	// Create the chart labels
	var labels = [];
	var today = new Date();
	for (i = 6; i >= 0; --i) {
		var d = new Date();
		d.setDate(today.getDate() - i);
		labels.push(d.yyyymmdd());
	}

	// The Chart data
	var data = {
		labels: labels,
		datasets: []
	}

	// Go through each asset
	var num = 0;
	for (var asset_id in stats.releases[release_id].assets) {
		var asset = stats.releases[release_id].assets[asset_id];
		var kees = Object.keys(asset.downloads);
		var prev_date = new Date();
		var prev_val = null;

		// Find the most recent valid data point before any of that data that shows up on the graph
		for(var i = 0; i < kees.length; i++) {
			if (kees[i] < labels[0]) {
				prev_date = labels[0];
				prev_val = asset.downloads[kees[i]];
			}
			else {
				break;
			}
		}

		// Pull the data out of the asset
		// Fill in any holes in the data. This is due to an asset not having any downloads for that day.
		var dataArray = new Array;
		var per_day = false;
		for(var i = 0; i < labels.length; i++) {
			var l = labels[i];
			if (asset.downloads[l] == null) {
				dataArray.push(per_day ? 0 : prev_val);
			}
			else {
				dataArray.push(per_day ? asset.downloads[l] - prev_val : asset.downloads[l]);
				prev_val = asset.downloads[l];
			}
		}

		// If "today" is in the download count, just use it,
		//if (asset.downloads[labels[labels.length - 1]] != null) {
		//	var l = labels[labels.length - 1];
		//	dataArray.push(per_day ? asset.downloads[l] - prev_val : asset.downloads[l]);
		//	prev_val = asset.downloads[l];
		//}
		//else {
		//	// Make another request to get an updated download count for today
		//	var url = apiRoot + "repos/" + user + "/" + repository + "/releases/" + release_id.toString();
		//	$.getJSON(url, function(blob) {
		//		data.datasets[0].data.push(blob.assets[0].download_count);
		//		// Technically has a race condition...oh well
		//		myLineChart.update();
		//	});
		//}

		// Add it to the dataset
		data.datasets.push({
			label: asset.name,
			fill: false,
			lineTension: 0.0,
			backgroundColor: "rgba(" + colorArray[num%4] + ",0.4)",
			borderColor: "rgba(" + colorArray[num%4] + ",1)",
			pointBorderColor: "rgba(" + colorArray[num%4] + ",1)",
			//pointHoverBackgroundColor: "rgba(75,192,192,1)",
			pointBackgroundColor: "#ffffff",
			//pointHoverBorderColor: "rgba(220,220,220,1)",
			pointRadius: 4,
			pointHoverRadius: 5,
			pointBorderWidth: 1,
			pointHoverBorderWidth: 2,
			pointHitRadius: 10,
			data: dataArray
		});
		num = num + 1;
	}

	// Graph it
	var ctx = $("#myChart");

	// Reset the chart if it has previously been created
	if (myLineChart) {
		myLineChart.destroy();
	}

	myLineChart = new Chart(ctx, {
		type: 'line',
		data: data,
		options: {
			responsive: true,
			maintainAspectRatio: true,
			animation: {duration: 0},
			scales: {
				yAxes: [{
					ticks: {
						beginAtZero: true,
						callback: function(value) {return value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");}
					},
					scaleLabel: {
						display: true,
						labelString: (per_day ? "Downloads per day" : "Total Downloads")
					}
				}]
			},
			title: {display: true, fontSize: 25, padding: 10, text: stats.releases[release_id].name},
			legend: {position: "bottom", labels: {boxWidth: 20}}
		}
	});

	// Scroll to the bottom of the page
	$('html, body').animate({ 
		scrollTop: $(document).height()-$(window).height()}, 
		400, 
		"swing"
	);
}

// The main function
$(function() {
	$("#loader-gif").hide();
	$("#stats-result").hide();

	validateInput();
	$("#username, #repository").keyup(validateInput);

	$("#username").change(getUserRepos);

	$("#originSelect").change(createGraph);

	$("#get-stats-button").click(function() {
		window.location = "?username=" + $("#username").val() + "&repository=" + $("#repository").val();
	});

	var username = getQueryVariable("username");
	var repository = getQueryVariable("repository");

	if(username != "" && repository != "") {
		$("#username").val(username);
		$("#repository").val(repository);
		validateInput();
		getUserRepos();
		$(".output").hide();
		$("#description").hide();
		$("#loader-gif").show();
		getGraphData();
	}
});
