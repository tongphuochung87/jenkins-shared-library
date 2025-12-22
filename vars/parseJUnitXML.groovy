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
        def xmlContent = readFile(file: xmlFile)
        def parser = new XmlParser()
        def testsuites = parser.parseText(xmlContent)
        
        def totalTests = 0
        def totalFailures = 0
        def totalDuration = 0.0
        def failedTestsList = []
        
        // Handle both <testsuites> wrapper and direct <testsuite>
        def suites = testsuites.name() == 'testsuites' ? testsuites.testsuite : [testsuites]
        
        suites.eachWithIndex { testsuite, index ->
            
            def suiteTests = (testsuite.@tests ?: '0') as Integer
            def suiteFailures = (testsuite.@failures ?: '0') as Integer
            def suiteTime = (testsuite.@time ?: '0') as Double
            def suiteName = testsuite.@name ?: 'Unknown'
            
            totalTests += suiteTests
            totalFailures += suiteFailures
            totalDuration += suiteTime
            
            // Collect failed test details
            def testcases = testsuite.testcase
            
            testcases.eachWithIndex { testcase, tcIndex ->
                def failures = testcase.failure
                if (failures && !failures.isEmpty()) {
                    def testName = testcase.@name
                    def failureMessage = failures[0].text()
                    failedTestsList.add("${testName}: ${failureMessage}")
                }
            }
        }
        
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
        echo "[ERROR] Error message: ${e.message}"
        e.printStackTrace()
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    return testResults
}
