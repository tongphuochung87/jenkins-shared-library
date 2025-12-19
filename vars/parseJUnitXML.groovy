#!/usr/bin/env groovy

def call(String xmlFile) {
    def testResults = [
        total: '0',
        passed: '0',
        failed: '0',
        duration: '0.0',
        failedTests: ''
    ]
    
    try {
        def xmlContent = readFile(file: xmlFile)
        def testsuites = new XmlSlurper().parseText(xmlContent)
        
        def totalTests = 0
        def totalFailures = 0
        def totalDuration = 0.0
        def failedTestsList = []
        
        // Access testsuite elements
        testsuites.testsuite.each { testsuite ->
            // Use attribute() method to access attributes safely
            def tests = testsuite.attribute('tests')
            def failures = testsuite.attribute('failures')
            def time = testsuite.attribute('time')
            
            totalTests += tests ? Integer.parseInt(tests) : 0
            totalFailures += failures ? Integer.parseInt(failures) : 0
            totalDuration += time ? Double.parseDouble(time) : 0.0
            
            // Collect failed test details
            testsuite.testcase.each { testcase ->
                if (testcase.failure.size() > 0) {
                    def testName = testcase.attribute('name')
                    def failureMessage = testcase.failure.text()
                    failedTestsList.add("${testName}: ${failureMessage}")
                }
            }
        }
        
        testResults.total = totalTests.toString()
        testResults.failed = totalFailures.toString()
        testResults.passed = (totalTests - totalFailures).toString()
        testResults.duration = String.format("%.1f", totalDuration)
        testResults.failedTests = failedTestsList.join('\n')
        
    } catch (Exception e) {
        echo "Error parsing JUnit XML: ${e.message}"
        echo "Exception class: ${e.class.name}"
        testResults.failedTests = "Error parsing test results: ${e.message}"
    }
    
    return testResults
}
