const fs = require('fs');

input = fs.readFileSync('1 7 8', 'utf8');
inputArr = input.split('\n');


output = '';

for(var i=0;i<inputArr.length;i++){
	var line = inputArr[i];
	var syn = line.split(" ");

	if(syn[0] == 'o'){
		output += line + '\n';
	}else if(syn[0] == 'f'){
		var delaySum = 0;
		var nonSkip = true;
		for(var j=i;j<inputArr.length;j++){
			var line_ = inputArr[j];
			var syn_ = line_.split(" ");

			if(syn_[0] == 'd'){
				delaySum += syn_[1]*1;
			}

			if(delaySum <= 50){
				if(syn_[0] == 'o' && (syn[1]*1) == (syn_[1]*1) && (syn[2]*1) == (syn_[2]*1)){
					nonSkip = false;
					break;
				}
			}else{
				break;
			}
		}
		if(nonSkip){
			output += line + '\n';
		}
	}else if(syn[0] == 'd'){
		output += line + '\n';
		console.log('(' + i + '/' + inputArr.length + ')');
	}
}

fs.writeFileSync('1 7 8 1', output, 'utf8');


/*
var lineReader = require('readline').createInterface({
  input: require('fs').createReadStream('1 7 8')
});

arr = [];

delaySum = 0;

lineReader.on('line', function (line) {
	var syn = line.split(" ");
	if(syn[0] == 'o'){
		arr.push(line);
	}else if(syn[0] == 'f'){
		arr.push(line);
	}else if(syn[0] == 'd'){
		delaySum += syn[1];
		if(delaySum <= 150){
			arr.push(line);
		}else{
			for(var i=0;i<arr.length;i++){
				var cmd = arr[i];
				var syn = cmd.split(" ");
				var nonSkip = true;
				if(syn[0] == 'f'){
					for(var j=i;j<arr.length;j++){
						var syn_ = arr[j].split(" ");
						if(syn_[0] == 'o' && syn[1] == syn_[1] && syn[2] == syn_[2]){
							nonSkip = false;
							break;
						}
					}
				}
				if(nonSkip){
					output += cmd + '\n';
				}
			}
			output += line + '\n';

			delaySum = 0;
			arr = [];
		}
	}
}).on('close', () => {
  console.log(output);
  fs.writeFileSync('1 7 8 1', output, 'utf8');
});*/