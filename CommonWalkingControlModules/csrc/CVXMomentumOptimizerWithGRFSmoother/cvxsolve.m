% Produced by CVXGEN, 2013-11-07 20:49:04 -0500.
% CVXGEN is Copyright (C) 2006-2012 Jacob Mattingley, jem@cvxgen.com.
% The code in this file is Copyright (C) 2006-2012 Jacob Mattingley.
% CVXGEN, or solvers produced by CVXGEN, cannot be used for commercial
% applications without prior written permission from Jacob Mattingley.

% Filename: cvxsolve.m.
% Description: Solution file, via cvx, for use with sample.m.
function [vars, status] = cvxsolve(params, settings)
A = params.A;
C = params.C;
Js = params.Js;
Lambda = params.Lambda;
Qphi = params.Qphi;
Qrho = params.Qrho;
WPhi = params.WPhi;
WRho = params.WRho;
WRhoSmoother = params.WRhoSmoother;
Ws = params.Ws;
b = params.b;
c = params.c;
phiMax = params.phiMax;
phiMin = params.phiMin;
ps = params.ps;
rhoMin = params.rhoMin;
rhoPrevious = params.rhoPrevious;
cvx_begin
  % Caution: automatically generated by cvxgen. May be incorrect.
  variable vd(34, 1);
  variable rho(64, 1);
  variable phi(10, 1);

  minimize(quad_form(A*vd - b, C) + quad_form(Js*vd - ps, Ws) + quad_form(rho, WRho) + quad_form(phi, WPhi) + quad_form(vd, Lambda) + quad_form(rho - rhoPrevious, WRhoSmoother));
  subject to
    Qrho*rho + Qphi*phi == A*vd + c;
    rho >= rhoMin;
    phiMin <= phi;
    phi <= phiMax;
cvx_end
vars.phi = phi;
vars.rho = rho;
vars.vd = vd;
status.cvx_status = cvx_status;
% Provide a drop-in replacement for csolve.
status.optval = cvx_optval;
status.converged = strcmp(cvx_status, 'Solved');
