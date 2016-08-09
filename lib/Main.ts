'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as VSCode from 'vscode';
import * as Path from 'path';
import * as FS from 'fs';
import * as PortFinder from 'portfinder';
import * as Net from 'net';
import * as ChildProcess from 'child_process';
import {LanguageClient, LanguageClientOptions, SettingMonitor, ServerOptions, StreamInfo} from 'vscode-languageclient';

PortFinder.basePort = 55282;

/** Called when extension is activated */
export function activate(context: VSCode.ExtensionContext) {
    let javaExecutablePath = findJavaExecutable('java');
    
    if (javaExecutablePath == null) {
        VSCode.window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
        
        return;
    }
        
    isJava8(javaExecutablePath).then(eight => {
        if (!eight) {
            VSCode.window.showErrorMessage('Java language support requires Java 8 (using ' + javaExecutablePath + ')');
            
            return;
        }
                    
        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            // Register the server for java documents
            documentSelector: ['java'],
            synchronize: {
                // Synchronize the setting section 'java' to the server
                // NOTE: this currently doesn't do anything
                configurationSection: 'java',
                // Notify the server about file changes to 'javaconfig.json' files contain in the workspace
                fileEvents: [
                    VSCode.workspace.createFileSystemWatcher('**/javaconfig.json'),
                    VSCode.workspace.createFileSystemWatcher('**/*.java')
                ]
            }
        }
        
        function createServer(): Promise<StreamInfo> {
            return new Promise((resolve, reject) => {
                PortFinder.getPort((err, port) => {
                    if (port != PortFinder.basePort) {
                        console.log("Port in use, connect instead of create.");
                        let socket = Net.createConnection({port: PortFinder.basePort}, () => {
                            console.log('connected to server!');
                        });
                        socket.on("connect", () => {
                            resolve({
                                reader: socket,
                                writer: socket
                            });
                        });                        
                    } else {
                        /*
                        let fatJar = Path.resolve(context.extensionPath, "out", "fat-jar.jar");
                        
                        let args = [
                            '-cp', fatJar, 
                            '-Djavacs.port=' + port,
                            'org.javacs.Main'
                        ];
                        
                        console.log(javaExecutablePath + ' ' + args.join(' '));
                        
                        Net.createServer(socket => {
                            console.log('Child process connected on port ' + port);
                            resolve({
                                reader: socket,
                                writer: socket
                            });
                        }).listen(port, () => {
                            let options = { stdio: 'inherit', cwd: VSCode.workspace.rootPath };
                            
                            // Do not start the child java process (debugging)
                            ChildProcess.execFile(javaExecutablePath, args, options);
                        });
                        */
                        console.log("cannot connect to language server");
                    }
                });
            });
        }

        // Create the language client and start the client.
        let client = new LanguageClient('Language Server Example', createServer, clientOptions);
        let disposable = client.start();

        // Push the disposable to the context's subscriptions so that the 
        // client can be deactivated on extension deactivation
        context.subscriptions.push(disposable);
        
        // Set indentation rules
        VSCode.languages.setLanguageConfiguration('java', {
            indentationRules: {
                // ^(.*\*/)?\s*\}.*$
                decreaseIndentPattern: /^(.*\*\/)?\s*\}.*$/,
                // ^.*\{[^}"']*$
                increaseIndentPattern: /^.*\{[^}"']*$/
            },
            wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\-\=\+\[\{\]\}\\\|\;\:\'\"\,\.\<\>\/\?\s]+)/g,
            comments: {
                lineComment: '//',
                blockComment: ['/*', '*/']
            },
            brackets: [
                ['{', '}'],
                ['[', ']'],
                ['(', ')'],
            ],
            onEnterRules: [
                {
                    // e.g. /** | */
                    beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
                    afterText: /^\s*\*\/$/,
                    action: { indentAction: VSCode.IndentAction.IndentOutdent, appendText: ' * ' }
                },
                {
                    // e.g. /** ...|
                    beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
                    action: { indentAction: VSCode.IndentAction.None, appendText: ' * ' }
                },
                {
                    // e.g.  * ...|
                    beforeText: /^(\t|(\ \ ))*\ \*(\ ([^\*]|\*(?!\/))*)?$/,
                    action: { indentAction: VSCode.IndentAction.None, appendText: '* ' }
                },
                {
                    // e.g.  */|
                    beforeText: /^(\t|(\ \ ))*\ \*\/\s*$/,
                    action: { indentAction: VSCode.IndentAction.None, removeText: 1 }
                }
            ],
            
            // TODO equivalent of this from typescript when replacement for __electricCharacterSupport API is released
            // __electricCharacterSupport: {
            //     docComment: { scope: 'comment.documentation', open: '/**', lineStart: ' * ', close: ' */' }
            // }
        });
    });
}

function isJava8(javaExecutablePath: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
        let result = ChildProcess.execFile(javaExecutablePath, ['-version'], { }, (error, stdout, stderr) => {
            let eight = stderr.indexOf('1.8') >= 0;
            
            resolve(eight);
        });
    });
} 

function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);

	// First search each JAVA_HOME bin folder
	if (process.env['JAVA_HOME']) {
		let workspaces = process.env['JAVA_HOME'].split(Path.delimiter);
		for (let i = 0; i < workspaces.length; i++) {
			let binpath = Path.join(workspaces[i], 'bin', binname);
			if (FS.existsSync(binpath)) {
				return binpath;
			}
		}
	}

	// Then search PATH parts
	if (process.env['PATH']) {
		let pathparts = process.env['PATH'].split(Path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = Path.join(pathparts[i], binname);
			if (FS.existsSync(binpath)) {
				return binpath;
			}
		}
	}
    
	// Else return the binary name directly (this will likely always fail downstream) 
	return null;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}

// this method is called when your extension is deactivated
export function deactivate() {
}