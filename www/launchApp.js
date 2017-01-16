var exec = require('cordova/exec');

module.exports = {
	setSpecification: function(intentsParameters, extraValues) {
	    console.log("Inside setSpecification");
		var receivedParameters = [intentsParameters];
		if(extraValues != undefined) {
			receivedParameters.push(extraValues);
		} else {
			receivedParameters.push(null);
		}
		
		return {
		    launch: function(launchComplete, launchError) {
				exec(launchComplete, launchError, "launchApp", "launch", receivedParameters);
            }
		}
	}
}

