var input = document.querySelector('#repl_input');

function inspected_eval(statement, callback) {
	//statement = statement.replace(/(\')/gm, '\\\'')

	console.log("evaluating: " + statement);
	return chrome.devtools.inspectedWindow.eval(statement, function(result, isException) {
		if(isException)
			console.error('isException while evaluating: ' + statement);
		else {
			callback(result);
		}
	});
}

function nop(r) {

}

function log_to_inspected_console(message) {
	if(typeof message == 'object') {
		inspected_eval('console.dir(JSON.parse(\'' + JSON.stringify(message) + '\'));', nop);
	}else
	if(typeof message == 'string') {
		inspected_eval('console.log(\'' + message + '\');', nop);
	}
}

function json_request(url, body, callback) {
	var request = new XMLHttpRequest();
	
	request.open('POST', 'http://192.168.1.107:3000/compile', false);
	request.setRequestHeader("Content-Type","application/json");

	var request_error = function(e) {
		log_to_inspected_console('request error: ' + request.responseText);
	};

	request.onload = function(e) {
		if(request.status === 200)
			callback(request.responseText);
		else
			request_error(e);
	};

	request.onerror = request_error;

	request.send(body);
}


function compile_to_js(statement, callback) {
	json_request('', JSON.stringify({statement: statement, ns: clojure_ns}), function(result) {
		callback(JSON.parse(result));
	});
}

function immigrate_expression(from_ns, to_ns) {
	return 'for(prop in ' + from_ns + ') ' + to_ns + '[prop] = ' + from_ns + ' [prop];';	
}

function immigrate(from_ns, to_ns) {
	inspected_eval(immigrate_expression(from_ns, to_ns), nop);
}

function create_ns(ns) {
	inspected_eval('try { goog.provide(\'' + ns + '\'); goog.require(\'cljs.core\'); ' + immigrate_expression('cljs.core', ns) + '} catch (ex){};', nop);
}

function submit_to_repl() {
	log_to_inspected_console('input statement: ' + input.value);	

   compile_to_js(input.value, function(json) {
   		log_to_inspected_console('compiled JS: ' + json.js.replace(/(\r\n|\n|\r)/gm, ' '));

   		var js = json.js;
   		if(json.isns) {
   			var ns = json.ns;
   			js = 'try { ' + js + '} catch(ex) {};';
   		}

		inspected_eval(js, function(evaluated) {
				log_to_inspected_console('evaluation result: ' + evaluated);

				if(json.isns) {
					immigrate('cljs.core', json.ns);
					clojure_ns = json.ns;
				}
		});
   });
}

document.querySelector('#repl_submit').addEventListener('click', function() {
   submit_to_repl();
}, false);

clojure_ns = 'cljs.user';

create_ns(clojure_ns);



