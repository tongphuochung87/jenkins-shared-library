#!/usr/bin/env groovy

def call(String xmlFile) {
    echo "[DEBUG] Starting parseJUnitXML for file: ${xmlFile}"
    
    def testResults = [
        total: '0',
        passed: '0',
        failed: '0',
        duration: '0.0',
        failedTests: ''
    ]
    
    try {
        echo "[DEBUG] Reading XML file content..."
        def xmlContent = readFile(file: xmlFile)
        echo "[DEBUG] XML file size: ${xmlContent.length()} bytes"
        echo "[DEBUG] First 500 characters of XML:\n${xmlContent.take(500)}"
        
        echo "[DEBUG] Parsing XML content..."
        def parser = new XmlParser()
        def testsuites = parser.parseText(xmlContent)
        echo "[DEBUG] Root element name: ${testsuites.name()}"
        
        def totalTests = 0
        def totalFailures = 0
        def totalDuration = 0.0
        def failedTestsList = []
        
        // Handle both <testsuites> wrapper and direct <testsuite>
        def suites = testsuites.name() == 'testsuites' ? testsuites.testsuite : [testsuites]
        echo "[DEBUG] Number of test suites found: ${suites.size()}"
        
        suites.eachWithIndex { testsuite, index ->
            echo "[DEBUG] Processing testsuite #${index + 1}"
            
            def suiteTests = (testsuite.@tests ?: '0') as Integer
            def suiteFailures = (testsuite.@failures ?: '0') as Integer
            def suiteTime = (testsuite.@time ?: '0') as Double
            def suiteName = testsuite.@name ?: 'Unknown'
            
            echo "[DEBUG] Suite '${suiteName}': tests=${suiteTests}, failures=${suiteFailures}, time=${suiteTime}s"
            
            totalTests += suiteTests
            totalFailures += suiteFailures
            totalDuration += suiteTime
            
            // Collect failed test details
            def testcases = testsuite.testcase
            echo "[DEBUG] Number of testcases in suite: ${testcases.size()}"
            
            testcases.eachWithIndex { testcase, tcIndex ->
                def failures = testcase.failure
                if (failures && !failures.isEmpty()) {
                    def testName = testcase.@name
                    def failureMessage = failures[0].text()
                    echo "[DEBUG] Failed test found: ${testName}"
                    echo "[DEBUG] Failure message: ${failureMessage.take(200)}${failureMessage.length() > 200 ? '...' : ''}"
                    failedTestsList.add("${testName}: ${failureMessage}")
                }
            }
        }
        
        echo "[DEBUG] Calculation complete:"
        echo "[DEBUG] - Total tests: ${totalTests}"
        echo "[DEBUG] - Total failures: ${totalFailures}"
        echo "[DEBUG] - Total passed: ${totalTests - totalFailures}"
        echo "[DEBUG] - Total duration: ${totalDuration}s"
        echo "[DEBUG] - Failed tests count: ${failedTestsList.size()}"
        
        testResults.total = totalTests.toString()
        testResults.failed = totalFailures.toString()
        testResults.passed = (totalTests - totalFailures).toString()
        testResults.duration = String.format("%.1f", totalDuration)
        testResults.failedTests = failedTestsList.join('\n')
        
        echo "[DEBUG] Final results object: ${testResults}"
        
    } catch (Exception e) {
        echo "[ERROR] Exception occurred during XML parsing"
        echo "[ERROR] Error message: ${e.message}"
        echo "[ERROR] Exception class: ${e.class.name}"
        echo "[ERROR] Stack trace:"
        e.printStackTrace()
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    
    echo "[DEBUG] Returning test results"
    return testResults
}
