# Tor Censorship Detector
# Usage: R --slave < detect-censorship.R

# Read CSV file containing daily user number estimates.
direct <- read.csv("direct-users.csv")

# Start plotting everything to a single PDF (with multiple pages).
pdf("detect-censorship.pdf")

# Convert the column containing daily Iranian users to a time series
# object, starting on the 263th day of 2009 with a frequency of 365 days.
# We're probably off by a day or two, but this should be fine for now.
all <- ts(direct$ir, start = c(2009, 263), frequency = 365)

# Uncomment to print the time series values.
#print(all)

# Let's try our approach for the last 365 days to see if we detect any
# blocking in that time period.  In the final version of this script, we'd
# only have a single run with i = 1.
for (i in 365:1) {
  idx <- length(direct$date) - i

  # Convert the daily Iranian users until i days in the past to a time
  # series object.
  x <- ts(direct$ir[1:idx], start = c(2009, 263), frequency = 365)

  # Apply an ARIMA(1, 0, 1) model to the time series.
  x.fit = arima(x, order = c(1, 0, 1))

  # Predict 10 dates ahead.
  x.fore=predict(x.fit, n.ahead=10)

  # Calculate a lower bound.  Here we use the predicted value minus three
  # standard errors.
  L = x.fore$pred - 3*x.fore$se

  # If the observed daily user number is lower than our predicted lower
  # bound, plot the data and lower bound.
  if (direct$ir[idx + 1] < L[1]) {

    # Plot the full time series.
    ts.plot(all)

    # Add a line for the ten predicted values.
    lines(L, col = "red", lwd = 2) 
  }
}

# Close the PDF device.
dev.off()

